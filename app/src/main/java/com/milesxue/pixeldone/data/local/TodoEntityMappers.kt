package com.milesxue.pixeldone.data.local

import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.normalizeChecklistState
import com.milesxue.pixeldone.domain.sync.SyncRecordState

data class TodoEntitySet(
    val metadata: TodoStateMetadataEntity,
    val checklists: List<TodoChecklistEntity>,
    val items: List<TodoItemEntity>,
)

fun TodoChecklistState.toTodoEntitySet(nowMillis: Long): TodoEntitySet {
    val checklistEntities = lists.mapIndexed { index, checklist ->
        TodoChecklistEntity(
            localId = checklist.id,
            sortIndex = index,
            name = checklist.name,
            createdAtMillis = checklist.createdAtMillis,
            updatedAtMillis = nowMillis,
            syncState = SyncRecordState.LOCAL_ONLY.name,
        )
    }
    val itemEntities = lists.flatMap { checklist ->
        checklist.items.mapIndexed { index, item ->
            TodoItemEntity(
                localId = item.id,
                checklistLocalId = checklist.id,
                sortIndex = index,
                title = item.title,
                priority = item.priority.name,
                dueAtMillis = item.dueAtMillis,
                completed = item.completed,
                createdAtMillis = item.createdAtMillis,
                updatedAtMillis = nowMillis,
                deletedAtMillis = item.trashedAtMillis,
                reminderRepeat = item.reminderRepeat.name,
                imageLocalName = item.imageFileName,
                trashedFromChecklistId = item.trashedFromChecklistId,
                trashedFromChecklistName = item.trashedFromChecklistName,
                trashedAtMillis = item.trashedAtMillis,
                syncState = SyncRecordState.LOCAL_ONLY.name,
            )
        }
    }
    return TodoEntitySet(
        metadata = TodoStateMetadataEntity(
            selectedListLocalId = selectedListId,
            updatedAtMillis = nowMillis,
        ),
        checklists = checklistEntities,
        items = itemEntities,
    )
}

fun todoEntitiesToState(
    metadata: TodoStateMetadataEntity?,
    checklists: List<TodoChecklistEntity>,
    items: List<TodoItemEntity>,
    fallbackCreatedAtMillis: Long,
): TodoChecklistState? {
    if (checklists.isEmpty()) return null
    val itemsByChecklist = items.groupBy { it.checklistLocalId }
    val lists = checklists.map { checklist ->
        TodoChecklist(
            id = checklist.localId,
            name = checklist.name,
            items = itemsByChecklist[checklist.localId].orEmpty()
                .sortedWith(compareBy<TodoItemEntity> { it.sortIndex }.thenBy { it.createdAtMillis })
                .mapNotNull { item -> item.toDomainTodoItem() },
            createdAtMillis = checklist.createdAtMillis,
        )
    }
    return normalizeChecklistState(
        state = TodoChecklistState(
            lists = lists,
            selectedListId = metadata?.selectedListLocalId.orEmpty(),
        ),
        fallbackCreatedAtMillis = fallbackCreatedAtMillis,
    )
}

private fun TodoItemEntity.toDomainTodoItem(): TodoItem? {
    val priority = TodoPriority.entries.firstOrNull { it.name == priority } ?: return null
    val repeat = ReminderRepeat.entries.firstOrNull { it.name == reminderRepeat } ?: ReminderRepeat.NONE
    return TodoItem(
        id = localId,
        title = title,
        priority = priority,
        dueAtMillis = dueAtMillis,
        completed = completed,
        createdAtMillis = createdAtMillis,
        reminderRepeat = repeat,
        imageFileName = imageLocalName,
        trashedFromChecklistId = trashedFromChecklistId,
        trashedFromChecklistName = trashedFromChecklistName,
        trashedAtMillis = trashedAtMillis ?: deletedAtMillis,
    )
}
