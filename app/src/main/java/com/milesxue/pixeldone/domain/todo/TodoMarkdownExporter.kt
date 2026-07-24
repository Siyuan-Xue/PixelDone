package com.milesxue.pixeldone.domain.todo

enum class MarkdownExportMode {
    SIMPLE,
    DETAILED,
}

data class MarkdownExportLabels(
    val priority: String,
    val due: String,
    val repeat: String,
    val none: String,
)

data class MarkdownExportFormatters(
    val priority: (TodoPriority) -> String,
    val due: (Long) -> String,
    val repeat: (ReminderRepeat) -> String,
)

fun exportChecklistToMarkdown(
    checklist: TodoChecklist,
    sortMode: SortMode,
    mode: MarkdownExportMode,
    labels: MarkdownExportLabels,
    formatters: MarkdownExportFormatters,
): String {
    val heading = "# ${checklist.name.toMarkdownPlainText()}"
    val items = visibleTodos(
        items = checklist.items,
        sortMode = sortMode,
        hideCompleted = false,
    )
    if (items.isEmpty()) return heading

    return buildString {
        append(heading)
        items.forEach { item ->
            append('\n')
            append("- [")
            append(if (item.completed) 'x' else ' ')
            append("] ")
            append(item.title.toMarkdownPlainText())
            if (mode == MarkdownExportMode.DETAILED) {
                appendMetadata(labels.priority, formatters.priority(item.priority))
                appendMetadata(
                    labels.due,
                    item.dueAtMillis.takeIf { it > 0L }?.let(formatters.due) ?: labels.none,
                )
                appendMetadata(
                    labels.repeat,
                    item.reminderRepeat
                        .takeIf { it != ReminderRepeat.NONE }
                        ?.let(formatters.repeat)
                        ?: labels.none,
                )
            }
        }
    }
}

private fun StringBuilder.appendMetadata(label: String, value: String) {
    append("\n  - ")
    append(label)
    append("：")
    append(value)
}

internal fun String.toMarkdownPlainText(): String {
    val flattened = replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return buildString(flattened.length) {
        flattened.forEach { character ->
            if (character in MarkdownCharactersToEscape) append('\\')
            append(character)
        }
    }
}

private val MarkdownCharactersToEscape = setOf(
    '\\',
    '`',
    '*',
    '_',
    '{',
    '}',
    '[',
    ']',
    '(',
    ')',
    '#',
    '+',
    '-',
    '.',
    '!',
    '>',
    '|',
)
