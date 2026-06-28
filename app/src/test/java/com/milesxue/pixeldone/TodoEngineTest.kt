package com.milesxue.pixeldone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TodoEngineTest {
    @Test
    fun createTodoItemRejectsBlankNamesAndTrimsValidNames() {
        assertNull(
            createTodoItem(
                id = "blank",
                titleInput = "   ",
                priority = TodoPriority.MEDIUM,
                dueAtMillis = 1_000L,
                createdAtMillis = 1L,
            ),
        )

        val item = createTodoItem(
            id = "task",
            titleInput = "  Ship build  ",
            priority = TodoPriority.HIGH,
            dueAtMillis = 1_000L,
            createdAtMillis = 1L,
        )

        assertEquals("Ship build", item?.title)
        assertFalse(item?.completed ?: true)
        assertEquals(ReminderRepeat.NONE, item?.reminderRepeat)
    }

    @Test
    fun prioritySortKeepsActiveFirstThenPriorityThenTime() {
        val items = listOf(
            item("done-high", TodoPriority.HIGH, due = 100L, completed = true, created = 1L),
            item("low", TodoPriority.LOW, due = 10L, created = 2L),
            item("high-late", TodoPriority.HIGH, due = 30L, created = 3L),
            item("high-early", TodoPriority.HIGH, due = 20L, created = 4L),
            item("xhigh", TodoPriority.XHIGH, due = 40L, created = 6L),
            item("mid", TodoPriority.MEDIUM, due = 5L, created = 5L),
        )

        val sortedIds = visibleTodos(items, SortMode.PRIORITY, hideCompleted = false)
            .map { it.id }

        assertEquals(
            listOf("xhigh", "high-early", "high-late", "mid", "low", "done-high"),
            sortedIds,
        )
    }

    @Test
    fun xhighPrioritySortsAboveHigh() {
        val items = listOf(
            item("high", TodoPriority.HIGH, due = 1L),
            item("xhigh", TodoPriority.XHIGH, due = 99L),
        )

        val sortedIds = visibleTodos(items, SortMode.PRIORITY, hideCompleted = false)
            .map { it.id }

        assertEquals(listOf("xhigh", "high"), sortedIds)
    }

    @Test
    fun timeSortKeepsActiveFirstThenTimeThenPriorityThenCreation() {
        val items = listOf(
            item("done-early", TodoPriority.HIGH, due = 1L, completed = true, created = 1L),
            item("late", TodoPriority.HIGH, due = 20L, created = 2L),
            item("same-time-low", TodoPriority.LOW, due = 10L, created = 3L),
            item("same-time-high", TodoPriority.HIGH, due = 10L, created = 4L),
            item("same-time-high-old", TodoPriority.HIGH, due = 10L, created = 1L),
        )

        val sortedIds = visibleTodos(items, SortMode.TIME, hideCompleted = false)
            .map { it.id }

        assertEquals(
            listOf("same-time-high-old", "same-time-high", "same-time-low", "late", "done-early"),
            sortedIds,
        )
    }

    @Test
    fun hideCompletedFiltersCompletedItems() {
        val items = listOf(
            item("active", TodoPriority.MEDIUM, due = 1L),
            item("done", TodoPriority.HIGH, due = 2L, completed = true),
        )

        val visibleIds = visibleTodos(items, SortMode.PRIORITY, hideCompleted = true)
            .map { it.id }

        assertEquals(listOf("active"), visibleIds)
    }

    @Test
    fun toggleCompletionChangesOnlyTheMatchingTodo() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L),
            item("two", TodoPriority.MEDIUM, due = 2L),
        )

        val updated = toggleTodoCompletion(items, "two")

        assertFalse(updated.first { it.id == "one" }.completed)
        assertTrue(updated.first { it.id == "two" }.completed)
    }

    @Test
    fun updateTodoItemChangesEditableFieldsAndKeepsExistingState() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L, completed = true, created = 10L),
            item("two", TodoPriority.LOW, due = 2L, created = 20L),
        )

        val updated = updateTodoItem(
            items = items,
            id = "one",
            titleInput = "  Updated task  ",
            priority = TodoPriority.HIGH,
            dueAtMillis = 99L,
            reminderRepeat = ReminderRepeat.WEEKLY,
        )

        val changed = updated?.first { it.id == "one" }
        assertEquals("Updated task", changed?.title)
        assertEquals(TodoPriority.HIGH, changed?.priority)
        assertEquals(99L, changed?.dueAtMillis)
        assertEquals(ReminderRepeat.WEEKLY, changed?.reminderRepeat)
        assertEquals(10L, changed?.createdAtMillis)
        assertTrue(changed?.completed ?: false)
        assertEquals(items.first { it.id == "two" }, updated?.first { it.id == "two" })
    }

    @Test
    fun updateTodoItemRejectsBlankNamesAndMissingIds() {
        val items = listOf(item("one", TodoPriority.MEDIUM, due = 1L))

        assertNull(
            updateTodoItem(
                items = items,
                id = "one",
                titleInput = " ",
                priority = TodoPriority.HIGH,
                dueAtMillis = 2L,
            ),
        )
        assertNull(
            updateTodoItem(
                items = items,
                id = "missing",
                titleInput = "Valid",
                priority = TodoPriority.HIGH,
                dueAtMillis = 2L,
            ),
        )
    }

    @Test
    fun deleteCompletedRemovesOnlyCompletedTodos() {
        val items = listOf(
            item("active", TodoPriority.MEDIUM, due = 1L),
            item("done", TodoPriority.MEDIUM, due = 2L, completed = true),
        )

        assertEquals(listOf("active"), deleteCompletedTodos(items).map { it.id })
    }

    @Test
    fun deleteTodoItemRemovesOnlyMatchingTodo() {
        val items = listOf(
            item("one", TodoPriority.MEDIUM, due = 1L),
            item("two", TodoPriority.HIGH, due = 2L, completed = true),
            item("three", TodoPriority.LOW, due = 3L),
        )

        assertEquals(listOf("one", "three"), deleteTodoItem(items, "two").map { it.id })
        assertEquals(items, deleteTodoItem(items, "missing"))
    }

    @Test
    fun alarmSchedulingRequiresActiveFutureTodos() {
        assertTrue(
            shouldScheduleTodoAlarm(
                item("future", TodoPriority.MEDIUM, due = 2_000L),
                nowMillis = 1_000L,
            ),
        )
        assertFalse(
            shouldScheduleTodoAlarm(
                item("done", TodoPriority.MEDIUM, due = 2_000L, completed = true),
                nowMillis = 1_000L,
            ),
        )
        assertFalse(
            shouldScheduleTodoAlarm(
                item("past", TodoPriority.MEDIUM, due = 1_000L),
                nowMillis = 1_000L,
            ),
        )
        assertTrue(
            shouldScheduleTodoAlarm(
                item(
                    id = "past-daily",
                    priority = TodoPriority.MEDIUM,
                    due = 1_000L,
                    repeat = ReminderRepeat.DAILY,
                ),
                nowMillis = 2_000L,
            ),
        )
    }

    @Test
    fun nextReminderAtMillisSupportsOneShotDailyAndWeeklyRules() {
        assertEquals(
            2_000L,
            nextReminderAtMillis(
                dueAtMillis = 2_000L,
                reminderRepeat = ReminderRepeat.NONE,
                nowMillis = 1_000L,
            ),
        )
        assertNull(
            nextReminderAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.NONE,
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            1_000L + DailyReminderIntervalMillis,
            nextReminderAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.DAILY,
                nowMillis = 1_000L,
            ),
        )
        assertEquals(
            1_000L + WeeklyReminderIntervalMillis,
            nextReminderAtMillis(
                dueAtMillis = 1_000L,
                reminderRepeat = ReminderRepeat.WEEKLY,
                nowMillis = 1_000L + DailyReminderIntervalMillis,
            ),
        )
    }

    @Test
    fun jsonCodecRoundTripsTodosWithEscapedText() {
        val items = listOf(
            item(
                id = "quoted",
                priority = TodoPriority.XHIGH,
                due = 1_700_000_000_000L,
                completed = false,
                created = 1L,
                title = "Review \"PixelDone\"\\notes\nnow",
                repeat = ReminderRepeat.DAILY,
            ),
            item(
                id = "done",
                priority = TodoPriority.LOW,
                due = 1_700_000_003_000L,
                completed = true,
                created = 2L,
                title = "Archive",
            ),
        )

        val decoded = TodoJsonCodec.decode(TodoJsonCodec.encode(items))

        assertEquals(items, decoded)
    }

    @Test
    fun jsonCodecDefaultsMissingReminderRepeatToNone() {
        val decoded = TodoJsonCodec.decode(
            """
            [
              {
                "id": "legacy",
                "title": "Legacy task",
                "priority": "HIGH",
                "dueAtMillis": 1700000000000,
                "completed": false,
                "createdAtMillis": 1
              }
            ]
            """.trimIndent(),
        )

        assertEquals(ReminderRepeat.NONE, decoded.single().reminderRepeat)
    }

    @Test
    fun initialChecklistStateWrapsLegacyTodosInMainList() {
        val legacyTodos = listOf(item("legacy", TodoPriority.MEDIUM, due = 1L))

        val state = createInitialChecklistState(legacyTodos, createdAtMillis = 42L)

        assertEquals(DefaultChecklistId, state.selectedListId)
        assertEquals(DefaultChecklistName, state.lists.single().name)
        assertEquals(42L, state.lists.single().createdAtMillis)
        assertEquals(legacyTodos, state.lists.single().items)
    }

    @Test
    fun createChecklistTrimsNamesSelectsNewListAndRejectsInvalidNames() {
        val state = createInitialChecklistState(emptyList(), createdAtMillis = 1L)

        val withWork = createTodoChecklist(
            state = state,
            id = "work",
            nameInput = "  Work  ",
            createdAtMillis = 2L,
        )!!

        assertEquals("work", withWork.selectedListId)
        assertEquals("Work", withWork.lists.last().name)
        assertNull(createTodoChecklist(withWork, "blank", " ", createdAtMillis = 3L))
        assertNull(createTodoChecklist(withWork, "duplicate", "work", createdAtMillis = 3L))
    }

    @Test
    fun renameChecklistKeepsNamesUniqueCaseInsensitively() {
        val state = createTodoChecklist(
            state = createInitialChecklistState(emptyList(), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!

        val renamed = renameTodoChecklist(state, DefaultChecklistId, "  Home  ")!!

        assertEquals("Home", renamed.lists.first { it.id == DefaultChecklistId }.name)
        assertNull(renameTodoChecklist(renamed, DefaultChecklistId, "WORK"))
        assertNull(renameTodoChecklist(renamed, "missing", "Other"))
    }

    @Test
    fun deleteChecklistKeepsAtLeastOneListAndFallsBackSelection() {
        val state = createTodoChecklist(
            state = createInitialChecklistState(emptyList(), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!

        val deletedSelected = deleteTodoChecklist(state, "work")!!

        assertEquals(listOf(DefaultChecklistId), deletedSelected.lists.map { it.id })
        assertEquals(DefaultChecklistId, deletedSelected.selectedListId)
        assertNull(deleteTodoChecklist(deletedSelected, DefaultChecklistId))
    }

    @Test
    fun updateChecklistItemsChangesOnlyTheMatchingListAndAllTodosFlattensLists() {
        val mainTodo = item("main", TodoPriority.HIGH, due = 1L)
        val workTodo = item("work-task", TodoPriority.LOW, due = 2L)
        val state = createTodoChecklist(
            state = createInitialChecklistState(listOf(mainTodo), createdAtMillis = 1L),
            id = "work",
            nameInput = "Work",
            createdAtMillis = 2L,
        )!!

        val updated = updateChecklistItems(state, "work", listOf(workTodo))!!

        assertEquals(listOf(mainTodo), updated.lists.first { it.id == DefaultChecklistId }.items)
        assertEquals(listOf(workTodo), updated.lists.first { it.id == "work" }.items)
        assertEquals(listOf(mainTodo, workTodo), allTodos(updated))
    }

    @Test
    fun checklistJsonCodecRoundTripsNestedState() {
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = DefaultChecklistId,
                    name = DefaultChecklistName,
                    items = listOf(item("one", TodoPriority.HIGH, due = 1L)),
                    createdAtMillis = 1L,
                ),
                TodoChecklist(
                    id = "work",
                    name = "Work \"Escaped\"",
                    items = listOf(item("two", TodoPriority.LOW, due = 2L, completed = true)),
                    createdAtMillis = 2L,
                ),
            ),
            selectedListId = "work",
        )

        val decoded = TodoJsonCodec.decodeState(
            json = TodoJsonCodec.encodeState(state),
            fallbackCreatedAtMillis = 99L,
        )

        assertEquals(state, decoded)
    }

    @Test
    fun defaultDueAtMillisUsesSameTimeTomorrowRoundedToMinute() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 6, 25, 9, 30, 42, 123_000_000)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val expected = LocalDateTime.of(2026, 6, 26, 9, 30)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, defaultDueAtMillis(now))
    }

    @Test
    fun completionSortDelayIsTwoSeconds() {
        assertEquals(2_000L, CompletionSortDelayMillis)
    }

    private fun item(
        id: String,
        priority: TodoPriority,
        due: Long,
        completed: Boolean = false,
        created: Long = due,
        title: String = id,
        repeat: ReminderRepeat = ReminderRepeat.NONE,
    ): TodoItem {
        return TodoItem(
            id = id,
            title = title,
            priority = priority,
            dueAtMillis = due,
            completed = completed,
            createdAtMillis = created,
            reminderRepeat = repeat,
        )
    }
}
