package com.milesxue.pixeldone.data.local

import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoChecklistState
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.normalizeChecklistState

data class TodoEntitySet(
    val metadata: TodoStateMetadataEntity,
    val checklists: List<TodoChecklistEntity>,
    val items: List<TodoItemEntity>,
    val tombstones: List<SyncTombstoneEntity> = emptyList(),
)

fun TodoChecklistState.toTodoEntitySet(
    nowMillis: Long,
    previousEntitySet: TodoEntitySet? = null,
): TodoEntitySet {
    val previousChecklistsById = previousEntitySet?.checklists.orEmpty().associateBy { it.localId }
    val previousItemsById = previousEntitySet?.items.orEmpty().associateBy { it.localId }
    val checklistEntities = lists.mapIndexed { index, checklist ->
        val previous = previousChecklistsById[checklist.id]
        val changed = previous?.matches(checklist, index) != true
        val ownerUserId = previous?.ownerUserId
        TodoChecklistEntity(
            localId = checklist.id,
            sortIndex = index,
            remoteId = previous?.remoteId,
            ownerUserId = ownerUserId,
            name = checklist.name,
            createdAtMillis = previous?.createdAtMillis ?: checklist.createdAtMillis,
            updatedAtMillis = if (changed) nowMillis else previous.updatedAtMillis,
            syncState = syncStateAfterLocalWrite(
                previousSyncState = previous?.syncState,
                ownerUserId = ownerUserId,
                changed = changed,
            ),
            lastSyncedAtMillis = previous?.lastSyncedAtMillis,
            remoteVersion = previous?.remoteVersion,
            lastSyncError = if (changed) null else previous?.lastSyncError,
        )
    }
    val visibleItemIds = lists.flatMap { it.items }.mapTo(mutableSetOf()) { it.id }
    val visibleItemEntities = lists.flatMap { checklist ->
        checklist.items.mapIndexed { index, item ->
            val previous = previousItemsById[item.id]
            val changed = previous?.matches(item, checklist.id, index) != true
            val imageChanged = previous?.imageLocalName != item.imageFileName
            val ownerUserId = previous?.ownerUserId
            val hasRemoteImage = previous?.imageRemotePath != null || previous?.imageRemoteVersion != null
            val imageSyncState = when {
                !imageChanged -> previous?.imageSyncState ?: TodoImageSyncState.LocalOnly
                item.imageFileName != null -> TodoImageSyncState.PendingUpload
                hasRemoteImage -> TodoImageSyncState.PendingDelete
                else -> TodoImageSyncState.LocalOnly
            }
            TodoItemEntity(
                localId = item.id,
                checklistLocalId = checklist.id,
                sortIndex = index,
                remoteId = previous?.remoteId,
                ownerUserId = ownerUserId,
                title = item.title,
                priority = item.priority.name,
                dueAtMillis = item.dueAtMillis,
                completed = item.completed,
                createdAtMillis = previous?.createdAtMillis ?: item.createdAtMillis,
                updatedAtMillis = if (changed) nowMillis else previous.updatedAtMillis,
                reminderRepeat = item.reminderRepeat.name,
                imageLocalName = item.imageFileName,
                imageRemotePath = previous?.imageRemotePath,
                imageSyncState = imageSyncState,
                trashedFromChecklistId = item.trashedFromChecklistId,
                trashedFromChecklistName = item.trashedFromChecklistName,
                trashedAtMillis = item.trashedAtMillis,
                syncState = syncStateAfterLocalWrite(
                    previousSyncState = previous?.syncState,
                    ownerUserId = ownerUserId,
                    changed = changed,
                ),
                lastSyncedAtMillis = previous?.lastSyncedAtMillis,
                remoteVersion = previous?.remoteVersion,
                lastSyncError = if (changed) null else previous?.lastSyncError,
                imageAttachmentId = previous?.imageAttachmentId.takeIf { item.imageFileName == null || !imageChanged },
                imageContentSha256 = previous?.imageContentSha256.takeIf { !imageChanged },
                imageContentType = previous?.imageContentType.takeIf { !imageChanged },
                imageByteSize = previous?.imageByteSize.takeIf { !imageChanged },
                imageUpdatedAtMillis = if (imageChanged) nowMillis else previous?.imageUpdatedAtMillis,
                imageRemoteVersion = previous?.imageRemoteVersion,
                imageLastSyncError = if (imageChanged) null else previous?.imageLastSyncError,
            )
        }
    }
    val visibleChecklistIds = lists.mapTo(mutableSetOf()) { it.id }
    val removedChecklistTombstones = previousEntitySet?.checklists.orEmpty()
        .filter { it.ownerUserId != null && it.localId !in visibleChecklistIds }
        .map { checklist ->
            SyncTombstoneEntity(
                ownerUserId = requireNotNull(checklist.ownerUserId),
                recordType = SyncRecordTypeChecklist,
                localId = checklist.localId,
                deletedAtMillis = nowMillis,
                remoteVersion = checklist.remoteVersion,
            )
        }
    val removedItemTombstones = previousEntitySet?.items.orEmpty()
        .filter { it.ownerUserId != null && it.localId !in visibleItemIds }
        .map { item ->
            SyncTombstoneEntity(
                ownerUserId = requireNotNull(item.ownerUserId),
                recordType = SyncRecordTypeItem,
                localId = item.localId,
                deletedAtMillis = nowMillis,
                remoteVersion = item.remoteVersion,
            )
        }
    val tombstones = (previousEntitySet?.tombstones.orEmpty() + removedChecklistTombstones + removedItemTombstones)
        .associateBy { Triple(it.ownerUserId, it.recordType, it.localId) }
        .values
        .toList()
    return TodoEntitySet(
        metadata = TodoStateMetadataEntity(
            selectedListLocalId = selectedListId,
            updatedAtMillis = nowMillis,
        ),
        checklists = checklistEntities,
        items = visibleItemEntities,
        tombstones = tombstones,
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

private fun syncStateAfterLocalWrite(
    previousSyncState: String?,
    ownerUserId: String?,
    changed: Boolean,
): String {
    if (ownerUserId == null) return SyncRecordState.LOCAL_ONLY.name
    if (changed) return SyncRecordState.NOT_SYNCED.name
    return previousSyncState ?: SyncRecordState.NOT_SYNCED.name
}

private fun TodoChecklistEntity.matches(checklist: TodoChecklist, sortIndex: Int): Boolean =
    this.sortIndex == sortIndex &&
        name == checklist.name &&
        createdAtMillis == checklist.createdAtMillis

private fun TodoItemEntity.matches(item: TodoItem, checklistLocalId: String, sortIndex: Int): Boolean =
    this.checklistLocalId == checklistLocalId &&
        this.sortIndex == sortIndex &&
        title == item.title &&
        priority == item.priority.name &&
        dueAtMillis == item.dueAtMillis &&
        completed == item.completed &&
        createdAtMillis == item.createdAtMillis &&
        reminderRepeat == item.reminderRepeat.name &&
        trashedFromChecklistId == item.trashedFromChecklistId &&
        trashedFromChecklistName == item.trashedFromChecklistName &&
        trashedAtMillis == item.trashedAtMillis

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
        trashedAtMillis = trashedAtMillis,
    )
}
