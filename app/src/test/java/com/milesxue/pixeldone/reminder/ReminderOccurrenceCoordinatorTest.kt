package com.milesxue.pixeldone.reminder

import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.data.todo.TodoStateStore
import com.milesxue.pixeldone.domain.todo.DailyReminderIntervalMillis
import com.milesxue.pixeldone.domain.todo.DefaultChecklistId
import com.milesxue.pixeldone.domain.todo.DefaultChecklistName
import com.milesxue.pixeldone.domain.todo.DefaultSnoozeIntervalMillis
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderOccurrenceCoordinatorTest {
    @Test
    fun visibleDeliveryPersistsReceiptBeforeAdvancingState() {
        val events = mutableListOf<String>()
        val dueAtMillis = 1_000L
        val store = RecordingTodoStateStore(
            initialState = stateWith(
                item(
                    id = "daily",
                    dueAtMillis = dueAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
            ),
            onUpdate = { events += "state" },
        )
        val history = RecordingOccurrenceHistory(
            onMarkDelivered = { events += "receipt" },
        )
        val coordinator = ReminderOccurrenceCoordinator(
            repository = TodoRepository(store),
            scheduler = RecordingReminderScheduler(),
            occurrenceHistory = history,
            requestSync = {},
            nowMillis = { dueAtMillis + 1L },
        )

        coordinator.recordDeliveredAndAdvance(
            todoIds = setOf("daily"),
            occurrenceAtMillis = dueAtMillis,
        )

        assertEquals(listOf("receipt", "state"), events)
    }

    @Test
    fun retryOfDeliveredOccurrenceIsReconciledButExcludedFromDispatchCandidates() {
        val occurrenceAtMillis = 1_000L
        val delivered = item(
            id = "delivered",
            dueAtMillis = occurrenceAtMillis,
            reminderRepeat = ReminderRepeat.DAILY,
        )
        val pending = item(
            id = "pending",
            dueAtMillis = occurrenceAtMillis,
            reminderRepeat = ReminderRepeat.DAILY,
        )
        val history = RecordingOccurrenceHistory().apply {
            markDelivered(setOf(delivered.id), occurrenceAtMillis)
        }

        val partition = partitionReminderOccurrence(
            items = listOf(delivered, pending),
            occurrenceAtMillis = occurrenceAtMillis,
            wasDelivered = history::wasDelivered,
        )

        assertEquals(setOf(delivered.id), partition.alreadyDeliveredTodoIds)
        assertEquals(listOf(pending.id), partition.dispatchCandidateItems.map { it.id })
    }

    @Test
    fun deliveredOccurrenceAdvancesAtomicallyToFirstFutureDeadlineAndRequestsSync() {
        val originalDueAtMillis = 1_000L
        val nowMillis = originalDueAtMillis + (3L * DailyReminderIntervalMillis) + 25L
        val occurrenceAtMillis = originalDueAtMillis + (3L * DailyReminderIntervalMillis)
        val store = RecordingTodoStateStore(
            stateWith(
                item(
                    id = "daily",
                    dueAtMillis = originalDueAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
            ),
        )
        val scheduler = RecordingReminderScheduler()
        val history = RecordingOccurrenceHistory()
        var syncRequests = 0
        val coordinator = ReminderOccurrenceCoordinator(
            repository = TodoRepository(store),
            scheduler = scheduler,
            occurrenceHistory = history,
            requestSync = { syncRequests += 1 },
            nowMillis = { nowMillis },
        )

        val changed = coordinator.recordDeliveredAndAdvance(
            todoIds = setOf("daily"),
            occurrenceAtMillis = occurrenceAtMillis,
        )

        assertTrue(changed)
        assertEquals(1, store.updateCalls)
        assertEquals(
            originalDueAtMillis + (4L * DailyReminderIntervalMillis),
            store.currentState.singleTodo().dueAtMillis,
        )
        assertEquals(occurrenceAtMillis, history.latestOccurrenceByTodoId["daily"])
        assertTrue(coordinator.wasDelivered("daily", occurrenceAtMillis))
        assertEquals(1, scheduler.syncCalls.size)
        assertEquals(originalDueAtMillis, scheduler.syncCalls.single().first.single().dueAtMillis)
        assertEquals(
            originalDueAtMillis + (4L * DailyReminderIntervalMillis),
            scheduler.syncCalls.single().second.single().dueAtMillis,
        )
        assertEquals(1, syncRequests)
    }

    @Test
    fun repeatingTheSameOccurrenceDoesNotAdvanceOrRequestSyncTwice() {
        val originalDueAtMillis = 1_000L
        val nowMillis = originalDueAtMillis + 5L
        val store = RecordingTodoStateStore(
            stateWith(
                item(
                    id = "daily",
                    dueAtMillis = originalDueAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
            ),
        )
        val scheduler = RecordingReminderScheduler()
        val history = RecordingOccurrenceHistory()
        var syncRequests = 0
        val coordinator = ReminderOccurrenceCoordinator(
            repository = TodoRepository(store),
            scheduler = scheduler,
            occurrenceHistory = history,
            requestSync = { syncRequests += 1 },
            nowMillis = { nowMillis },
        )

        assertTrue(
            coordinator.recordDeliveredAndAdvance(
                todoIds = setOf("daily"),
                occurrenceAtMillis = originalDueAtMillis,
            ),
        )
        assertFalse(
            coordinator.recordDeliveredAndAdvance(
                todoIds = setOf("daily"),
                occurrenceAtMillis = originalDueAtMillis,
            ),
        )

        assertEquals(2, store.updateCalls)
        assertEquals(1, scheduler.syncCalls.size)
        assertEquals(1, syncRequests)
        assertEquals(originalDueAtMillis, history.latestOccurrenceByTodoId["daily"])
        assertTrue(coordinator.wasDelivered("daily", originalDueAtMillis))
    }

    @Test
    fun snoozeMarksOccurrenceAndPersistsOneAtomicStateTransition() {
        val occurrenceAtMillis = 20_000L
        val nowMillis = 30_000L
        val store = RecordingTodoStateStore(
            stateWith(
                item(
                    id = "daily",
                    dueAtMillis = occurrenceAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
            ),
        )
        val scheduler = RecordingReminderScheduler()
        val history = RecordingOccurrenceHistory()
        var syncRequests = 0
        val coordinator = ReminderOccurrenceCoordinator(
            repository = TodoRepository(store),
            scheduler = scheduler,
            occurrenceHistory = history,
            requestSync = { syncRequests += 1 },
            nowMillis = { nowMillis },
        )

        val changed = coordinator.snoozeTodos(
            todoIds = setOf("daily"),
            occurrenceAtMillis = occurrenceAtMillis,
        )

        assertTrue(changed)
        assertEquals(1, store.updateCalls)
        assertEquals(nowMillis + DefaultSnoozeIntervalMillis, store.currentState.singleTodo().dueAtMillis)
        assertEquals(occurrenceAtMillis, history.latestOccurrenceByTodoId["daily"])
        assertEquals(1, scheduler.syncCalls.size)
        assertEquals(1, syncRequests)
    }

    @Test
    fun schedulerFailureStillRequestsCloudSyncAfterAtomicPersistence() {
        val dueAtMillis = 1_000L
        val store = RecordingTodoStateStore(
            stateWith(
                item(
                    id = "daily",
                    dueAtMillis = dueAtMillis,
                    reminderRepeat = ReminderRepeat.DAILY,
                ),
            ),
        )
        val scheduler = RecordingReminderScheduler(syncFailure = IllegalStateException("alarm"))
        var syncRequests = 0
        val coordinator = ReminderOccurrenceCoordinator(
            repository = TodoRepository(store),
            scheduler = scheduler,
            occurrenceHistory = RecordingOccurrenceHistory(),
            requestSync = { syncRequests += 1 },
            nowMillis = { dueAtMillis + 1L },
        )

        val failure = runCatching {
            coordinator.recordDeliveredAndAdvance(
                todoIds = setOf("daily"),
                occurrenceAtMillis = dueAtMillis,
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(1, store.updateCalls)
        assertEquals(1, scheduler.syncCalls.size)
        assertEquals(1, syncRequests)
    }

    private fun stateWith(vararg items: TodoItem): TodoChecklistState {
        return TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = DefaultChecklistId,
                    name = DefaultChecklistName,
                    items = items.toList(),
                    createdAtMillis = 1L,
                ),
            ),
            selectedListId = DefaultChecklistId,
        )
    }

    private fun item(
        id: String,
        dueAtMillis: Long,
        reminderRepeat: ReminderRepeat,
    ): TodoItem {
        return TodoItem(
            id = id,
            title = id,
            priority = TodoPriority.MEDIUM,
            dueAtMillis = dueAtMillis,
            completed = false,
            createdAtMillis = 1L,
            reminderRepeat = reminderRepeat,
        )
    }

    private fun TodoChecklistState.singleTodo(): TodoItem = lists.single().items.single()
}

private class RecordingTodoStateStore(
    initialState: TodoChecklistState,
    private val onUpdate: () -> Unit = {},
) : TodoStateStore {
    var currentState = initialState
        private set
    var updateCalls = 0
        private set

    override fun loadTodoState(nowMillis: Long): TodoChecklistState = currentState

    override fun saveTodoState(state: TodoChecklistState) {
        currentState = state
    }

    override fun updateTodoState(
        transform: (TodoChecklistState) -> TodoChecklistState,
    ): TodoChecklistState {
        updateCalls += 1
        onUpdate()
        return transform(currentState).also { updated -> currentState = updated }
    }

    override fun observeTodoState(onChange: () -> Unit): () -> Unit = {}
}

private class RecordingOccurrenceHistory(
    private val onMarkDelivered: () -> Unit = {},
) : ReminderOccurrenceHistory {
    val latestOccurrenceByTodoId = mutableMapOf<String, Long>()

    override fun wasDelivered(todoId: String, occurrenceAtMillis: Long): Boolean {
        return latestOccurrenceByTodoId.getOrDefault(todoId, Long.MIN_VALUE) >= occurrenceAtMillis
    }

    override fun markDelivered(todoIds: Set<String>, occurrenceAtMillis: Long) {
        onMarkDelivered()
        todoIds.forEach { todoId ->
            latestOccurrenceByTodoId[todoId] = maxOf(
                latestOccurrenceByTodoId.getOrDefault(todoId, Long.MIN_VALUE),
                occurrenceAtMillis,
            )
        }
    }
}

private class RecordingReminderScheduler(
    private val syncFailure: Throwable? = null,
) : ReminderScheduler {
    val syncCalls = mutableListOf<Pair<List<TodoItem>, List<TodoItem>>>()

    override fun sync(
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> {
        syncCalls += previousItems to currentItems
        syncFailure?.let { throw it }
        return emptySet()
    }

    override fun schedule(item: TodoItem): Set<ReminderCapability> = emptySet()

    override fun canScheduleExactAlarms(): Boolean = true

    override fun cancel(itemId: String) = Unit
}
