package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.local.toTodoEntitySet
import com.milesxue.pixeldone.data.local.todoEntitiesToState
import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.todo.DefaultChecklistId
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.TrashChecklistId
import com.milesxue.pixeldone.domain.todo.createInitialChecklistState
import com.milesxue.pixeldone.domain.todo.moveTodoItemToTrash
import com.milesxue.pixeldone.domain.todo.updateChecklistItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoEntityMapperTest {
    @Test
    fun mapsDomainStateToLocalOnlyEntitiesWithFutureSyncFields() {
        val initial = createInitialChecklistState(
            items = listOf(
                TodoItem(
                    id = "todo-1",
                    title = "One",
                    priority = TodoPriority.HIGH,
                    dueAtMillis = 2_000L,
                    completed = false,
                    createdAtMillis = 1_000L,
                    imageFileName = "image.jpg",
                ),
            ),
            createdAtMillis = 1L,
        )
        val withTrash = moveTodoItemToTrash(initial, DefaultChecklistId, "todo-1", 3_000L)!!
        val entities = withTrash.toTodoEntitySet(nowMillis = 4_000L)
        val trashItem = entities.items.single { it.localId == "todo-1" }

        assertEquals(withTrash.selectedListId, entities.metadata.selectedListLocalId)
        assertEquals(SyncRecordState.LOCAL_ONLY.name, entities.checklists.first().syncState)
        assertEquals(SyncRecordState.LOCAL_ONLY.name, trashItem.syncState)
        assertEquals(SyncRecordState.LOCAL_ONLY.name, trashItem.imageSyncState)
        assertNull(trashItem.remoteId)
        assertNull(trashItem.ownerUserId)
        assertNull(trashItem.lastSyncedAtMillis)
        assertNull(trashItem.remoteVersion)
        assertEquals(3_000L, trashItem.deletedAtMillis)
        assertEquals(TrashChecklistId, trashItem.checklistLocalId)
        assertEquals("image.jpg", trashItem.imageLocalName)
    }


    @Test
    fun localWritePreservesRemoteMetadataAndMarksOwnedRecordDirty() {
        val initial = createInitialChecklistState(
            items = listOf(
                TodoItem(
                    id = "todo-1",
                    title = "One",
                    priority = TodoPriority.HIGH,
                    dueAtMillis = 2_000L,
                    completed = false,
                    createdAtMillis = 1_000L,
                ),
            ),
            createdAtMillis = 1L,
        )
        val previousBase = initial.toTodoEntitySet(nowMillis = 2_000L)
        val previous = previousBase.copy(
            items = previousBase.items.map { item ->
                item.copy(
                    remoteId = "remote-todo-1",
                    ownerUserId = "user-1",
                    syncState = SyncRecordState.SYNCED.name,
                    lastSyncedAtMillis = 2_100L,
                    remoteVersion = 7L,
                )
            },
        )
        val updatedTodo = initial.lists.first().items.first().copy(title = "One updated")
        val updatedState = updateChecklistItems(initial, initial.selectedListId, listOf(updatedTodo))!!

        val saved = updatedState.toTodoEntitySet(
            nowMillis = 3_000L,
            previousEntitySet = previous,
        )
        val savedItem = saved.items.single { it.localId == "todo-1" }

        assertEquals("remote-todo-1", savedItem.remoteId)
        assertEquals("user-1", savedItem.ownerUserId)
        assertEquals(SyncRecordState.NOT_SYNCED.name, savedItem.syncState)
        assertEquals(2_100L, savedItem.lastSyncedAtMillis)
        assertEquals(7L, savedItem.remoteVersion)
    }
    @Test
    fun roundTripsEntitiesBackToNormalizedDomainState() {
        val state = TodoChecklistState(
            lists = listOf(
                TodoChecklist(
                    id = "work",
                    name = "WORK",
                    items = listOf(
                        TodoItem("a", "A", TodoPriority.LOW, 10L, false, 1L),
                        TodoItem("b", "B", TodoPriority.MEDIUM, 20L, true, 2L),
                    ),
                    createdAtMillis = 1L,
                ),
            ),
            selectedListId = "work",
        )
        val entities = state.toTodoEntitySet(nowMillis = 30L)

        val restored = todoEntitiesToState(
            metadata = entities.metadata,
            checklists = entities.checklists,
            items = entities.items,
            fallbackCreatedAtMillis = 30L,
        )!!

        assertEquals("work", restored.selectedListId)
        assertEquals(listOf("work", TrashChecklistId, "settings"), restored.lists.map { it.id })
        assertEquals(listOf("a", "b"), restored.lists.first { it.id == "work" }.items.map { it.id })
    }
}
