package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.*
import org.junit.Assert.assertEquals
import org.junit.Test

class UserTodoStateMergeTest {
    @Test
    fun localCompletionPreservesConcurrentCloudTitle() {
        val original = state(TodoItem("one", "Original", TodoPriority.MEDIUM, 1L, false, 1L))
        val local = state(original.lists.first().items.single().copy(completed = true))
        val cloud = state(original.lists.first().items.single().copy(title = "Cloud title"))

        val merged = mergeUserTodoStateChange(original, local, cloud).lists.first().items.single()

        assertEquals("Cloud title", merged.title)
        assertEquals(true, merged.completed)
    }

    @Test
    fun localTrashMovePreservesConcurrentCloudTitle() {
        val item = TodoItem("one", "Original", TodoPriority.MEDIUM, 1L, false, 1L)
        val original = TodoChecklistState(
            lists = listOf(
                TodoChecklist("main", "MAIN", listOf(item), 1L),
                TodoChecklist(TrashChecklistId, TrashChecklistName, emptyList(), 2L),
            ),
            selectedListId = "main",
        )
        val trashed = item.copy(
            title = "Original",
            trashedFromChecklistId = "main",
            trashedFromChecklistName = "MAIN",
            trashedAtMillis = 5L,
        )
        val local = original.copy(lists = listOf(
            original.lists[0].copy(items = emptyList()),
            original.lists[1].copy(items = listOf(trashed)),
        ))
        val cloud = original.copy(lists = listOf(
            original.lists[0].copy(items = listOf(item.copy(title = "Cloud title"))),
            original.lists[1],
        ))

        val merged = mergeUserTodoStateChange(original, local, cloud)
        val result = merged.lists.first { it.id == TrashChecklistId }.items.single()
        assertEquals("Cloud title", result.title)
        assertEquals(5L, result.trashedAtMillis)
    }

    private fun state(item: TodoItem) = TodoChecklistState(
        lists = listOf(TodoChecklist("main", "MAIN", listOf(item), 1L)),
        selectedListId = "main",
    )
}
