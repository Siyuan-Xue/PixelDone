package com.milesxue.pixeldone.domain.todo

/**
 * 清单与回收站规则。
 * 这些函数都是纯函数：输入旧状态，返回新状态，不直接读写磁盘，也不触碰 Android API。
 */

fun createInitialChecklistState(
    items: List<TodoItem>,
    createdAtMillis: Long,
): TodoChecklistState {
    return TodoChecklistState(
        lists = listOf(
            TodoChecklist(
                id = DefaultChecklistId,
                name = DefaultChecklistName,
                items = items,
                createdAtMillis = createdAtMillis,
            ),
            TodoChecklist(
                id = TrashChecklistId,
                name = TrashChecklistName,
                items = emptyList(),
                createdAtMillis = createdAtMillis,
            ),
            TodoChecklist(
                id = SettingsChecklistId,
                name = SettingsChecklistName,
                items = emptyList(),
                createdAtMillis = createdAtMillis,
            ),
        ),
        selectedListId = DefaultChecklistId,
    )
}

fun selectedChecklistOf(state: TodoChecklistState): TodoChecklist {
    return state.lists.firstOrNull { it.id == state.selectedListId }
        ?: state.lists.firstOrNull(::isNormalChecklist)
        ?: state.lists.first()
}

fun allTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.flatMap { it.items }
}

fun normalTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.filter(::isNormalChecklist).flatMap { it.items }
}

fun trashTodos(state: TodoChecklistState): List<TodoItem> {
    return state.lists.firstOrNull(::isTrashChecklist)?.items.orEmpty()
}

fun normalChecklistCount(state: TodoChecklistState): Int {
    return state.lists.count(::isNormalChecklist)
}

fun isTrashChecklist(checklist: TodoChecklist): Boolean {
    return checklist.id == TrashChecklistId
}

fun isTrashChecklistId(id: String): Boolean {
    return id == TrashChecklistId
}

fun isSettingsChecklist(checklist: TodoChecklist): Boolean {
    return checklist.id == SettingsChecklistId
}

fun isSettingsChecklistId(id: String): Boolean {
    return id == SettingsChecklistId
}

fun isSpecialChecklist(checklist: TodoChecklist): Boolean {
    return isTrashChecklist(checklist) || isSettingsChecklist(checklist)
}

fun isSpecialChecklistId(id: String): Boolean {
    return isTrashChecklistId(id) || isSettingsChecklistId(id)
}

fun isNormalChecklist(checklist: TodoChecklist): Boolean {
    return !isSpecialChecklist(checklist)
}

fun isTrashedTodo(item: TodoItem): Boolean {
    return item.trashedAtMillis != null || item.trashedFromChecklistId != null
}

fun activeTodoCount(checklist: TodoChecklist): Int {
    return checklist.items.count { !it.completed }
}

fun completedTodoCount(checklist: TodoChecklist): Int {
    return checklist.items.count { it.completed }
}

fun isChecklistNameAvailable(
    state: TodoChecklistState,
    nameInput: String,
    editingId: String? = null,
): Boolean {
    val name = nameInput.trim()
    if (name.isEmpty()) return false
    if (name.equals(TrashChecklistName, ignoreCase = true)) return false
    if (name.equals(SettingsChecklistName, ignoreCase = true)) return false

    return state.lists.none { checklist ->
        checklist.id != editingId && checklist.name.equals(name, ignoreCase = true)
    }
}

fun createTodoChecklist(
    state: TodoChecklistState,
    id: String,
    nameInput: String,
    createdAtMillis: Long,
): TodoChecklistState? {
    if (isSpecialChecklistId(id)) return null

    val name = nameInput.trim()
    if (!isChecklistNameAvailable(state, name)) return null

    val checklist = TodoChecklist(
        id = id,
        name = name,
        items = emptyList(),
        createdAtMillis = createdAtMillis,
    )
    return state.copy(
        lists = state.lists.filter(::isNormalChecklist) +
            checklist +
            state.lists.filter(::isSpecialChecklist),
        selectedListId = checklist.id,
    )
}

fun renameTodoChecklist(
    state: TodoChecklistState,
    id: String,
    nameInput: String,
): TodoChecklistState? {
    if (isSpecialChecklistId(id)) return null

    val name = nameInput.trim()
    if (!isChecklistNameAvailable(state, name, editingId = id)) return null

    var found = false
    val updatedLists = state.lists.map { checklist ->
        if (checklist.id == id) {
            found = true
            checklist.copy(name = name)
        } else {
            checklist
        }
    }

    return if (found) state.copy(lists = updatedLists) else null
}

fun selectTodoChecklist(state: TodoChecklistState, id: String): TodoChecklistState? {
    if (state.lists.none { it.id == id }) return null
    return state.copy(selectedListId = id)
}

fun deleteTodoChecklist(
    state: TodoChecklistState,
    id: String,
    trashedAtMillis: Long,
): TodoChecklistState? {
    if (isSpecialChecklistId(id)) return null
    if (normalChecklistCount(state) <= 1) return null

    val checklistToDelete = state.lists.firstOrNull { it.id == id && isNormalChecklist(it) } ?: return null
    val trash = state.lists.firstOrNull(::isTrashChecklist) ?: return null
    val movedItems = checklistToDelete.items.map { item ->
        item.toTrashItem(
            sourceChecklist = checklistToDelete,
            trashedAtMillis = trashedAtMillis,
        )
    }
    val updatedLists = state.lists
        .filterNot { it.id == id }
        .map { checklist ->
            if (isTrashChecklist(checklist)) {
                trash.copy(items = trash.items + movedItems)
            } else {
                checklist
            }
        }

    val selectedListId = if (state.selectedListId == id) {
        updatedLists.first(::isNormalChecklist).id
    } else {
        state.selectedListId
    }
    return state.copy(
        lists = updatedLists,
        selectedListId = selectedListId,
    )
}

fun moveTodoItemToTrash(
    state: TodoChecklistState,
    checklistId: String,
    todoId: String,
    trashedAtMillis: Long,
): TodoChecklistState? {
    if (isSpecialChecklistId(checklistId)) return null
    val sourceChecklist = state.lists.firstOrNull { it.id == checklistId && isNormalChecklist(it) }
        ?: return null
    val itemToMove = sourceChecklist.items.firstOrNull { it.id == todoId } ?: return null

    return moveTodosToTrash(
        state = state,
        sourceChecklist = sourceChecklist,
        todoIds = setOf(itemToMove.id),
        trashedAtMillis = trashedAtMillis,
    )
}

fun moveCompletedTodosToTrash(
    state: TodoChecklistState,
    checklistId: String,
    trashedAtMillis: Long,
): TodoChecklistState? {
    if (isSpecialChecklistId(checklistId)) return null
    val sourceChecklist = state.lists.firstOrNull { it.id == checklistId && isNormalChecklist(it) }
        ?: return null
    val completedIds = sourceChecklist.items
        .filter { it.completed }
        .mapTo(mutableSetOf()) { it.id }
    if (completedIds.isEmpty()) return null

    return moveTodosToTrash(
        state = state,
        sourceChecklist = sourceChecklist,
        todoIds = completedIds,
        trashedAtMillis = trashedAtMillis,
    )
}

fun moveTodoItemsToChecklist(
    state: TodoChecklistState,
    sourceChecklistId: String,
    targetChecklistId: String,
    todoIds: Collection<String>,
): TodoChecklistState? {
    if (todoIds.isEmpty()) return null
    if (sourceChecklistId == targetChecklistId) return null
    if (isSpecialChecklistId(sourceChecklistId) || isSpecialChecklistId(targetChecklistId)) return null

    val sourceChecklist = state.lists.firstOrNull {
        it.id == sourceChecklistId && isNormalChecklist(it)
    } ?: return null
    val targetChecklist = state.lists.firstOrNull {
        it.id == targetChecklistId && isNormalChecklist(it)
    } ?: return null

    val sourceItemsById = sourceChecklist.items.associateBy { it.id }
    val movedItems = todoIds.distinct().mapNotNull { sourceItemsById[it] }
    if (movedItems.isEmpty()) return null

    val movedIds = movedItems.mapTo(mutableSetOf()) { it.id }
    val updatedLists = state.lists.map { checklist ->
        when (checklist.id) {
            sourceChecklist.id -> checklist.copy(items = checklist.items.filterNot { it.id in movedIds })
            targetChecklist.id -> checklist.copy(items = checklist.items + movedItems)
            else -> checklist
        }
    }

    return state.copy(
        lists = updatedLists,
        selectedListId = targetChecklistId,
    )
}

fun restoreTodoFromTrash(
    state: TodoChecklistState,
    todoId: String,
    restoredAtMillis: Long,
): TodoChecklistState? {
    val trash = state.lists.firstOrNull(::isTrashChecklist) ?: return null
    val trashedItem = trash.items.firstOrNull { it.id == todoId } ?: return null
    val originalChecklistId = trashedItem.trashedFromChecklistId
        ?.takeIf { it.isNotBlank() && !isSpecialChecklistId(it) }
    val originalChecklist = originalChecklistId?.let { id ->
        state.lists.firstOrNull { it.id == id && isNormalChecklist(it) }
    }
    val fallbackChecklist = state.lists.firstOrNull(::isNormalChecklist)
    val targetChecklistId = originalChecklist?.id
        ?: originalChecklistId
        ?: fallbackChecklist?.id
        ?: DefaultChecklistId

    val listsWithTarget = when {
        originalChecklist != null -> state.lists
        originalChecklistId != null -> {
            val restoredChecklist = TodoChecklist(
                id = originalChecklistId,
                name = restoredChecklistName(trashedItem),
                items = emptyList(),
                createdAtMillis = restoredAtMillis,
            )
            state.lists.filter(::isNormalChecklist) +
                restoredChecklist +
                state.lists.filter(::isSpecialChecklist)
        }
        fallbackChecklist != null -> state.lists
        else -> {
            val fallbackMain = TodoChecklist(
                id = DefaultChecklistId,
                name = DefaultChecklistName,
                items = emptyList(),
                createdAtMillis = restoredAtMillis,
            )
            state.lists.filter(::isNormalChecklist) +
                fallbackMain +
                state.lists.filter(::isSpecialChecklist)
        }
    }

    val selectedListId = if (state.lists.any { it.id == state.selectedListId }) {
        state.selectedListId
    } else {
        targetChecklistId
    }

    val restoredItem = trashedItem.copy(
        trashedFromChecklistId = null,
        trashedFromChecklistName = null,
        trashedAtMillis = null,
    )
    val updatedLists = listsWithTarget.map { checklist ->
        when {
            isTrashChecklist(checklist) -> {
                checklist.copy(items = checklist.items.filterNot { it.id == todoId })
            }
            checklist.id == targetChecklistId -> {
                checklist.copy(items = checklist.items + restoredItem)
            }
            else -> checklist
        }
    }

    return state.copy(
        lists = updatedLists,
        selectedListId = selectedListId,
    )
}

fun deleteAllTrashTodos(state: TodoChecklistState): TodoChecklistState {
    return state.copy(
        lists = state.lists.map { checklist ->
            if (isTrashChecklist(checklist)) {
                checklist.copy(items = emptyList())
            } else {
                checklist
            }
        },
    )
}

fun updateChecklistItems(
    state: TodoChecklistState,
    checklistId: String,
    items: List<TodoItem>,
): TodoChecklistState? {
    if (isSettingsChecklistId(checklistId)) return null

    var found = false
    val updatedLists = state.lists.map { checklist ->
        if (checklist.id == checklistId) {
            found = true
            checklist.copy(items = items)
        } else {
            checklist
        }
    }

    return if (found) state.copy(lists = updatedLists) else null
}

fun normalizeChecklistState(
    state: TodoChecklistState,
    fallbackCreatedAtMillis: Long,
): TodoChecklistState {
    val trashItems = mutableListOf<TodoItem>()
    var trashCreatedAtMillis: Long? = null
    var settingsCreatedAtMillis: Long? = null
    val validNormalLists = state.lists.mapNotNull { checklist ->
        val name = checklist.name.trim()
        if (checklist.id.isBlank() || name.isEmpty()) {
            return@mapNotNull null
        }

        if (isTrashChecklist(checklist)) {
            trashCreatedAtMillis = trashCreatedAtMillis ?: checklist.createdAtMillis
            trashItems += checklist.items.map { item ->
                if (isTrashedTodo(item)) {
                    item
                } else {
                    item.copy(
                        trashedFromChecklistId = DefaultChecklistId,
                        trashedFromChecklistName = DefaultChecklistName,
                        trashedAtMillis = fallbackCreatedAtMillis,
                    )
                }
            }
            return@mapNotNull null
        }

        if (isSettingsChecklist(checklist)) {
            settingsCreatedAtMillis = settingsCreatedAtMillis ?: checklist.createdAtMillis
            trashItems += checklist.items.filter(::isTrashedTodo)
            return@mapNotNull null
        }

        val normalName = sanitizedNormalChecklistName(name)
        val normalItems = checklist.items.filterNot(::isTrashedTodo)
        trashItems += checklist.items.filter(::isTrashedTodo)
        checklist.copy(
            name = normalName,
            items = normalItems,
        )
    }
    val normalLists = validNormalLists.ifEmpty {
        listOf(
            TodoChecklist(
                id = DefaultChecklistId,
                name = DefaultChecklistName,
                items = emptyList(),
                createdAtMillis = fallbackCreatedAtMillis,
            ),
        )
    }
    val trashChecklist = TodoChecklist(
        id = TrashChecklistId,
        name = TrashChecklistName,
        items = trashItems,
        createdAtMillis = trashCreatedAtMillis ?: fallbackCreatedAtMillis,
    )
    val settingsChecklist = TodoChecklist(
        id = SettingsChecklistId,
        name = SettingsChecklistName,
        items = emptyList(),
        createdAtMillis = settingsCreatedAtMillis ?: fallbackCreatedAtMillis,
    )
    val validLists = normalLists + trashChecklist + settingsChecklist

    val selectedId = if (validLists.any { it.id == state.selectedListId }) {
        state.selectedListId
    } else {
        normalLists.first().id
    }
    return state.copy(
        lists = validLists,
        selectedListId = selectedId,
    )
}

private fun moveTodosToTrash(
    state: TodoChecklistState,
    sourceChecklist: TodoChecklist,
    todoIds: Set<String>,
    trashedAtMillis: Long,
): TodoChecklistState? {
    val trash = state.lists.firstOrNull(::isTrashChecklist) ?: return null
    val movedItems = sourceChecklist.items
        .filter { it.id in todoIds }
        .map { item ->
            item.toTrashItem(
                sourceChecklist = sourceChecklist,
                trashedAtMillis = trashedAtMillis,
            )
        }
    if (movedItems.isEmpty()) return null

    val updatedLists = state.lists.map { checklist ->
        when {
            checklist.id == sourceChecklist.id -> {
                checklist.copy(items = checklist.items.filterNot { it.id in todoIds })
            }
            isTrashChecklist(checklist) -> {
                trash.copy(items = trash.items + movedItems)
            }
            else -> checklist
        }
    }

    return state.copy(lists = updatedLists)
}

private fun TodoItem.toTrashItem(
    sourceChecklist: TodoChecklist,
    trashedAtMillis: Long,
): TodoItem {
    return copy(
        trashedFromChecklistId = sourceChecklist.id,
        trashedFromChecklistName = sourceChecklist.name,
        trashedAtMillis = trashedAtMillis,
    )
}

private fun restoredChecklistName(item: TodoItem): String {
    val sourceName = item.trashedFromChecklistName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return DefaultChecklistName

    return if (sourceName.equals(TrashChecklistName, ignoreCase = true) ||
        sourceName.equals(SettingsChecklistName, ignoreCase = true)
    ) {
        DefaultChecklistName
    } else {
        sourceName
    }
}

private fun sanitizedNormalChecklistName(name: String): String {
    return when {
        name.equals(TrashChecklistName, ignoreCase = true) -> "$TrashChecklistName LIST"
        name.equals(SettingsChecklistName, ignoreCase = true) -> "$SettingsChecklistName LIST"
        else -> name
    }
}
