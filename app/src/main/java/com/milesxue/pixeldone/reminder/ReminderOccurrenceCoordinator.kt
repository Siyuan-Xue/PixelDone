package com.milesxue.pixeldone.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.advanceRepeatingTodosAfterReminder
import com.milesxue.pixeldone.domain.todo.normalTodos
import com.milesxue.pixeldone.domain.todo.snoozeTodosAfterReminder
import com.milesxue.pixeldone.domain.todo.todosDueForReminder

internal interface ReminderOccurrenceHistory {
    fun wasDelivered(todoId: String, occurrenceAtMillis: Long): Boolean
    fun markDelivered(todoIds: Set<String>, occurrenceAtMillis: Long)
}

internal data class ReminderOccurrencePartition(
    val alreadyDeliveredTodoIds: Set<String>,
    val dispatchCandidateItems: List<TodoItem>,
)

internal fun partitionReminderOccurrence(
    items: List<TodoItem>,
    occurrenceAtMillis: Long,
    wasDelivered: (todoId: String, occurrenceAtMillis: Long) -> Boolean,
): ReminderOccurrencePartition {
    val deliveredTodoIds = todosDueForReminder(
        items = items,
        firedDueAtMillis = occurrenceAtMillis,
    )
        .filter { item ->
            wasDelivered(item.id, occurrenceAtMillis)
        }
        .mapTo(mutableSetOf()) { item -> item.id }
    return ReminderOccurrencePartition(
        alreadyDeliveredTodoIds = deliveredTodoIds,
        dispatchCandidateItems = items.filterNot { item -> item.id in deliveredTodoIds },
    )
}

internal class SharedPreferencesReminderOccurrenceHistory(
    private val preferences: SharedPreferences,
) : ReminderOccurrenceHistory {
    @Synchronized
    override fun wasDelivered(todoId: String, occurrenceAtMillis: Long): Boolean {
        if (occurrenceAtMillis <= 0L) return false
        return preferences.getLong(key(todoId), Long.MIN_VALUE) >= occurrenceAtMillis
    }

    @SuppressLint("ApplySharedPref")
    @Synchronized
    override fun markDelivered(todoIds: Set<String>, occurrenceAtMillis: Long) {
        if (todoIds.isEmpty() || occurrenceAtMillis <= 0L) return
        preferences.edit().apply {
            todoIds.forEach { todoId ->
                val key = key(todoId)
                val current = preferences.getLong(key, Long.MIN_VALUE)
                if (occurrenceAtMillis > current) {
                    putLong(key, occurrenceAtMillis)
                }
            }
        }.commit()
    }

    private fun key(todoId: String): String = "$KEY_PREFIX$todoId"

    companion object {
        private const val PREFERENCES_NAME = "todo_reminder_occurrences"
        private const val KEY_PREFIX = "last_delivered_"

        fun create(context: Context): SharedPreferencesReminderOccurrenceHistory {
            return SharedPreferencesReminderOccurrenceHistory(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
        }
    }
}

internal class ReminderOccurrenceCoordinator(
    private val repository: TodoRepository,
    private val scheduler: ReminderScheduler,
    private val occurrenceHistory: ReminderOccurrenceHistory,
    private val requestSync: () -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun wasDelivered(todoId: String, occurrenceAtMillis: Long): Boolean {
        return occurrenceHistory.wasDelivered(todoId, occurrenceAtMillis)
    }

    fun recordDeliveredAndAdvance(
        todoIds: Set<String>,
        occurrenceAtMillis: Long,
    ): Boolean {
        occurrenceHistory.markDelivered(todoIds, occurrenceAtMillis)
        return advanceDeliveredTodos(todoIds, occurrenceAtMillis)
    }

    fun advanceDeliveredTodos(
        todoIds: Set<String>,
        occurrenceAtMillis: Long,
    ): Boolean {
        if (todoIds.isEmpty()) return false
        return updateState { state ->
            advanceRepeatingTodosAfterReminder(
                state = state,
                todoIds = todoIds,
                nowMillis = maxOf(nowMillis(), occurrenceAtMillis),
            )
        }
    }

    fun snoozeTodos(
        todoIds: Set<String>,
        occurrenceAtMillis: Long,
    ): Boolean {
        if (todoIds.isEmpty()) return false
        occurrenceHistory.markDelivered(todoIds, occurrenceAtMillis)
        return updateState { state ->
            snoozeTodosAfterReminder(
                state = state,
                todoIds = todoIds,
                nowMillis = nowMillis(),
            )
        }
    }

    private fun updateState(
        transform: (TodoChecklistState) -> TodoChecklistState?,
    ): Boolean {
        var stateBefore: TodoChecklistState? = null
        val stateAfter = repository.updateTodoState { latest ->
            stateBefore = latest
            transform(latest) ?: latest
        }
        val previous = requireNotNull(stateBefore)
        if (stateAfter == previous) return false

        val scheduleResult = runCatching {
            scheduler.sync(
                previousItems = normalTodos(previous),
                currentItems = normalTodos(stateAfter),
            )
        }
        val syncResult = runCatching(requestSync)
        scheduleResult.getOrThrow()
        syncResult.getOrThrow()
        return true
    }
}
