package com.milesxue.pixeldone.ui.todo

import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.createTodoItem
import com.milesxue.pixeldone.domain.todo.updateTodoItem

internal sealed interface EditorMode {
    data object None : EditorMode
    data object NewTask : EditorMode
    data class EditTask(val id: String) : EditorMode
    data object NewChecklist : EditorMode
    data class EditChecklist(val id: String) : EditorMode
    data object CloudSignIn : EditorMode
    data object ChangePassword : EditorMode
}

internal sealed interface TodoSubmissionTarget {
    data object Create : TodoSubmissionTarget
    data class Update(val id: String) : TodoSubmissionTarget
}

internal data class TodoSubmissionResult(
    val items: List<TodoItem>,
    val affectedTodoId: String,
)

internal fun todoSubmissionTarget(editorMode: EditorMode): TodoSubmissionTarget? =
    when (editorMode) {
        EditorMode.NewTask -> TodoSubmissionTarget.Create
        is EditorMode.EditTask -> TodoSubmissionTarget.Update(editorMode.id)
        else -> null
    }

internal fun applyTodoSubmission(
    items: List<TodoItem>,
    target: TodoSubmissionTarget,
    titleInput: String,
    priority: TodoPriority,
    dueAtMillis: Long,
    reminderRepeat: ReminderRepeat,
    createdAtMillis: Long,
    newTodoId: () -> String,
): TodoSubmissionResult? {
    return when (target) {
        TodoSubmissionTarget.Create -> {
            val id = newTodoId()
            val item = createTodoItem(
                id = id,
                titleInput = titleInput,
                priority = priority,
                dueAtMillis = dueAtMillis,
                createdAtMillis = createdAtMillis,
                reminderRepeat = reminderRepeat,
            ) ?: return null
            TodoSubmissionResult(items = items + item, affectedTodoId = id)
        }

        is TodoSubmissionTarget.Update -> {
            val updatedItems = updateTodoItem(
                items = items,
                id = target.id,
                titleInput = titleInput,
                priority = priority,
                dueAtMillis = dueAtMillis,
                reminderRepeat = reminderRepeat,
            ) ?: return null
            TodoSubmissionResult(items = updatedItems, affectedTodoId = target.id)
        }
    }
}
