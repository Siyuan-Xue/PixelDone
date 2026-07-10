package com.milesxue.pixeldone.domain.todo

/**
 * Applies only the fields changed by the UI between [before] and [after] to the
 * latest Room state. Remote changes committed while the UI gesture was in flight
 * are therefore preserved instead of being overwritten by an old full snapshot.
 */
fun mergeUserTodoStateChange(
    before: TodoChecklistState,
    after: TodoChecklistState,
    latest: TodoChecklistState,
): TodoChecklistState {
    if (before == latest) return after
    val beforeLists = before.lists.associateBy { it.id }
    val afterLists = after.lists.associateBy { it.id }
    var lists = latest.lists

    val removedListIds = beforeLists.keys - afterLists.keys
    lists = lists.filterNot { it.id in removedListIds }
    after.lists.filter { it.id !in beforeLists }.forEach { added ->
        if (lists.none { it.id == added.id }) lists = lists + added
    }
    lists = lists.map { current ->
        val old = beforeLists[current.id]
        val changed = afterLists[current.id]
        if (old != null && changed != null && old.name != changed.name) current.copy(name = changed.name) else current
    }

    val beforeItems = before.lists.flatMap { list -> list.items.map { it.id to (list.id to it) } }.toMap()
    val afterItems = after.lists.flatMap { list -> list.items.map { it.id to (list.id to it) } }.toMap()
    val removedItemIds = beforeItems.keys - afterItems.keys
    lists = lists.map { it.copy(items = it.items.filterNot { item -> item.id in removedItemIds }) }

    afterItems.filterKeys { it !in beforeItems }.forEach { (id, destination) ->
        val (listId, item) = destination
        if (lists.none { list -> list.items.any { it.id == id } }) {
            lists = lists.map { list -> if (list.id == listId) list.copy(items = list.items + item) else list }
        }
    }

    (beforeItems.keys intersect afterItems.keys).forEach { id ->
        val (oldListId, old) = requireNotNull(beforeItems[id])
        val (newListId, changed) = requireNotNull(afterItems[id])
        if (old == changed && oldListId == newListId) return@forEach
        val current = lists.firstNotNullOfOrNull { list -> list.items.firstOrNull { it.id == id } } ?: return@forEach
        val patched = current.copy(
            title = changed.title.takeIf { it != old.title } ?: current.title,
            priority = changed.priority.takeIf { it != old.priority } ?: current.priority,
            dueAtMillis = changed.dueAtMillis.takeIf { it != old.dueAtMillis } ?: current.dueAtMillis,
            completed = changed.completed.takeIf { it != old.completed } ?: current.completed,
            reminderRepeat = changed.reminderRepeat.takeIf { it != old.reminderRepeat } ?: current.reminderRepeat,
            imageFileName = if (changed.imageFileName != old.imageFileName) changed.imageFileName else current.imageFileName,
            trashedFromChecklistId = if (changed.trashedFromChecklistId != old.trashedFromChecklistId) changed.trashedFromChecklistId else current.trashedFromChecklistId,
            trashedFromChecklistName = if (changed.trashedFromChecklistName != old.trashedFromChecklistName) changed.trashedFromChecklistName else current.trashedFromChecklistName,
            trashedAtMillis = if (changed.trashedAtMillis != old.trashedAtMillis) changed.trashedAtMillis else current.trashedAtMillis,
        )
        val destinationId = if (newListId != oldListId) newListId else
            lists.firstOrNull { list -> list.items.any { it.id == id } }?.id ?: newListId
        lists = lists.map { list -> list.copy(items = list.items.filterNot { it.id == id }) }
        lists = lists.map { list -> if (list.id == destinationId) list.copy(items = list.items + patched) else list }
    }

    after.lists.forEach { desiredList ->
        val previousOrder = beforeLists[desiredList.id]?.items?.map { it.id }
        val desiredOrder = desiredList.items.map { it.id }
        if (previousOrder != desiredOrder) {
            lists = lists.map { current ->
                if (current.id != desiredList.id) current else {
                    val byId = current.items.associateBy { it.id }
                    val ordered = desiredOrder.mapNotNull(byId::get) + current.items.filter { it.id !in desiredOrder }
                    current.copy(items = ordered)
                }
            }
        }
    }

    val desiredListOrder = after.lists.map { it.id }
    if (before.lists.map { it.id } != desiredListOrder) {
        val byId = lists.associateBy { it.id }
        lists = desiredListOrder.mapNotNull(byId::get) + lists.filter { it.id !in desiredListOrder }
    }
    return TodoChecklistState(
        lists = lists,
        selectedListId = if (before.selectedListId != after.selectedListId) after.selectedListId else latest.selectedListId,
    )
}
