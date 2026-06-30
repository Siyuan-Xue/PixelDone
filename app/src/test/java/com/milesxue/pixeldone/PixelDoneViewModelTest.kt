package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.todo.TodoRepository
import com.milesxue.pixeldone.domain.todo.ReminderCapability
import com.milesxue.pixeldone.domain.todo.SettingsChecklistId
import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState
import com.milesxue.pixeldone.domain.todo.updateChecklistItems
import com.milesxue.pixeldone.reminder.ReminderScheduler
import com.milesxue.pixeldone.ui.todo.PixelDoneAction
import com.milesxue.pixeldone.ui.todo.PixelDoneViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelDoneViewModelTest {
    @Test
    fun replaceChecklistStatePersistsAndSyncsReminders() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val scheduler = FakeReminderScheduler()
        val viewModel = PixelDoneViewModel(repository, scheduler)
        val todo = TodoItem("one", "One", TodoPriority.HIGH, 2_000L, false, 1L)
        val updated = updateChecklistItems(initial, initial.selectedListId, listOf(todo))!!

        viewModel.onAction(PixelDoneAction.ReplaceChecklistState(updated))

        assertEquals(updated, repository.state.value)
        assertEquals(updated, viewModel.uiState.value.checklistState)
        assertEquals(listOf(todo), scheduler.lastCurrentItems)
    }

    @Test
    fun replaceChecklistStateExcludesSettingsItemsFromReminderSync() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val scheduler = FakeReminderScheduler()
        val viewModel = PixelDoneViewModel(repository, scheduler)
        val normalTodo = TodoItem("normal", "Normal", TodoPriority.HIGH, 2_000L, false, 1L)
        val settingsTodo = TodoItem("settings", "Settings", TodoPriority.HIGH, 2_000L, false, 1L)
        val withNormal = updateChecklistItems(initial, initial.selectedListId, listOf(normalTodo))!!
        val corruptedSettings = withNormal.copy(
            lists = withNormal.lists.map { checklist ->
                if (checklist.id == SettingsChecklistId) {
                    checklist.copy(items = listOf(settingsTodo))
                } else {
                    checklist
                }
            },
        )

        viewModel.onAction(PixelDoneAction.ReplaceChecklistState(corruptedSettings))

        assertEquals(listOf(normalTodo), scheduler.lastCurrentItems)
    }

    @Test
    fun uiPreferenceActionsUpdateStateWithoutTouchingRepository() {
        val initial = createInitialChecklistState(emptyList(), createdAtMillis = 1L)
        val repository = TodoRepository(InMemoryTodoStateStore(initial))
        val viewModel = PixelDoneViewModel(repository, FakeReminderScheduler())

        viewModel.onAction(PixelDoneAction.SetSortMode(SortMode.TIME))
        viewModel.onAction(PixelDoneAction.SetHideCompleted(true))
        viewModel.onAction(PixelDoneAction.SetDeadlineCountdownVisible(true))

        assertEquals(SortMode.TIME, viewModel.uiState.value.sortMode)
        assertEquals(true, viewModel.uiState.value.hideCompleted)
        assertEquals(true, viewModel.uiState.value.showDeadlineCountdown)
        assertEquals(initial, repository.state.value)
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    var lastPreviousItems: List<TodoItem> = emptyList()
    var lastCurrentItems: List<TodoItem> = emptyList()

    override fun sync(
        previousItems: List<TodoItem>,
        currentItems: List<TodoItem>,
    ): Set<ReminderCapability> {
        lastPreviousItems = previousItems
        lastCurrentItems = currentItems
        return emptySet()
    }

    override fun schedule(item: TodoItem): Set<ReminderCapability> = emptySet()

    override fun canScheduleExactAlarms(): Boolean = true

    override fun cancel(itemId: String) = Unit
}
