package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.MarkdownExportFormatters
import com.milesxue.pixeldone.domain.todo.MarkdownExportLabels
import com.milesxue.pixeldone.domain.todo.MarkdownExportMode
import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoChecklist
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import com.milesxue.pixeldone.domain.todo.exportChecklistToMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoMarkdownExporterTest {
    private val labels = MarkdownExportLabels(
        priority = "Priority",
        due = "Due",
        repeat = "Repeat",
        none = "None",
    )
    private val formatters = MarkdownExportFormatters(
        priority = { it.name },
        due = { "DUE-$it" },
        repeat = { it.name },
    )

    @Test
    fun simpleExportIncludesAllItemsAndCompletionStateInCurrentSortOrder() {
        val markdown = exportChecklistToMarkdown(
            checklist = checklist(
                name = "Ship #3",
                items = listOf(
                    todo("done", completed = true, priority = TodoPriority.XHIGH, dueAtMillis = 1L),
                    todo("active", completed = false, priority = TodoPriority.LOW, dueAtMillis = 2L),
                ),
            ),
            sortMode = SortMode.PRIORITY,
            mode = MarkdownExportMode.SIMPLE,
            labels = labels,
            formatters = formatters,
        )

        assertEquals(
            "# Ship \\#3\n- [ ] active\n- [x] done",
            markdown,
        )
    }

    @Test
    fun detailedExportAddsLocalizedMetadataAndFlattensTitles() {
        val markdown = exportChecklistToMarkdown(
            checklist = checklist(
                name = "List",
                items = listOf(
                    todo(
                        title = "Write *notes*\nfor [team]",
                        completed = false,
                        priority = TodoPriority.HIGH,
                        dueAtMillis = 42L,
                        repeat = ReminderRepeat.WEEKLY,
                    ),
                ),
            ),
            sortMode = SortMode.TIME,
            mode = MarkdownExportMode.DETAILED,
            labels = labels,
            formatters = formatters,
        )

        assertEquals(
            """
            # List
            - [ ] Write \*notes\* for \[team\]
              - Priority：HIGH
              - Due：DUE-42
              - Repeat：WEEKLY
            """.trimIndent(),
            markdown,
        )
    }

    @Test
    fun detailedExportUsesNoneAndEmptyChecklistOnlyCopiesHeading() {
        val noMetadata = exportChecklistToMarkdown(
            checklist = checklist(
                name = "Plain",
                items = listOf(todo("Task", dueAtMillis = 0L)),
            ),
            sortMode = SortMode.PRIORITY,
            mode = MarkdownExportMode.DETAILED,
            labels = labels,
            formatters = formatters,
        )
        val empty = exportChecklistToMarkdown(
            checklist = checklist("Empty", emptyList()),
            sortMode = SortMode.PRIORITY,
            mode = MarkdownExportMode.SIMPLE,
            labels = labels,
            formatters = formatters,
        )

        assertEquals(true, noMetadata.contains("- Due：None\n  - Repeat：None"))
        assertEquals("# Empty", empty)
    }

    private fun checklist(name: String, items: List<TodoItem>) = TodoChecklist(
        id = "list",
        name = name,
        items = items,
        createdAtMillis = 0L,
    )

    private fun todo(
        title: String,
        completed: Boolean = false,
        priority: TodoPriority = TodoPriority.MEDIUM,
        dueAtMillis: Long = 0L,
        repeat: ReminderRepeat = ReminderRepeat.NONE,
    ) = TodoItem(
        id = title,
        title = title,
        priority = priority,
        dueAtMillis = dueAtMillis,
        completed = completed,
        createdAtMillis = 0L,
        reminderRepeat = repeat,
    )
}
