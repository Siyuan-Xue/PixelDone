package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.widget.configuredWidgetChecklistId
import com.milesxue.pixeldone.widget.widgetChecklists
import com.milesxue.pixeldone.widget.widgetItemId
import com.milesxue.pixeldone.widget.widgetTodos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PixelDoneWidgetRulesTest {
    @Test
    fun widgetShowsOnlyNormalListsAndUnfinishedPriorityOrderedTodos() {
        val normal = checklist(
            id = "normal",
            items = listOf(
                todo("done", TodoPriority.XHIGH, completed = true),
                todo("low", TodoPriority.LOW),
                todo("high", TodoPriority.HIGH),
            ),
        )
        val trash = checklist(id = TrashChecklistId, items = emptyList())
        val state = TodoChecklistState(
            lists = listOf(normal, trash),
            selectedListId = normal.id,
        )

        assertEquals(listOf(normal), widgetChecklists(state))
        assertEquals(listOf("high", "low"), widgetTodos(normal).map { it.title })
    }

    @Test
    fun widgetItemsUseStableDistinctIdsForScrollPosition() {
        assertEquals(widgetItemId("todo-a"), widgetItemId("todo-a"))
        assertNotEquals(widgetItemId("todo-a"), widgetItemId("todo-b"))
    }

    @Test
    fun widgetConfigurationRequiresAnExplicitValidSelection() {
        val main = checklist(id = "main", items = emptyList())

        assertEquals(null, configuredWidgetChecklistId(listOf(main), configuredChecklistId = null))
        assertEquals(null, configuredWidgetChecklistId(listOf(main), configuredChecklistId = "missing"))
        assertEquals("main", configuredWidgetChecklistId(listOf(main), configuredChecklistId = "main"))
    }

    private fun checklist(id: String, items: List<TodoItem>) = TodoChecklist(
        id = id,
        name = id,
        items = items,
        createdAtMillis = 0L,
    )

    private fun todo(
        title: String,
        priority: TodoPriority,
        completed: Boolean = false,
    ) = TodoItem(
        id = title,
        title = title,
        priority = priority,
        dueAtMillis = 1L,
        completed = completed,
        createdAtMillis = 0L,
        reminderRepeat = ReminderRepeat.NONE,
    )
}
