package com.milesxue.pixeldone.reminder

import com.milesxue.pixeldone.domain.todo.ReminderRepeat
import com.milesxue.pixeldone.domain.todo.TodoItem
import com.milesxue.pixeldone.domain.todo.TodoPriority
import java.nio.charset.StandardCharsets
import java.util.Base64

internal const val ActiveXHighAlarmMaxAgeMillis = 12L * 60L * 60L * 1000L

internal data class ActiveXHighAlarm(
    val todoIds: List<String>,
    val todoTitles: List<String>,
    val firedDueAtMillis: Long,
    val companionTodoIds: List<String> = emptyList(),
    val companionTodoTitles: List<String> = emptyList(),
    val companionTodoPriorities: List<String> = emptyList(),
    val companionTodoRepeats: List<String> = emptyList(),
    val updatedAtMillis: Long,
) {
    val displayCount: Int
        get() = todoIds.size

    fun primaryTitle(): String = todoTitles.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Todo due"

    fun isStale(nowMillis: Long): Boolean {
        return updatedAtMillis <= 0L ||
            nowMillis - updatedAtMillis > ActiveXHighAlarmMaxAgeMillis
    }

    fun companionShortItems(): List<TodoItem> {
        return companionTodoIds.mapIndexedNotNull { index, todoId ->
            val id = todoId.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            TodoItem(
                id = id,
                title = companionTodoTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Todo due",
                priority = companionTodoPriorities.getOrNull(index)
                    ?.let { name -> TodoPriority.entries.firstOrNull { it.name == name } }
                    ?: TodoPriority.MEDIUM,
                dueAtMillis = firedDueAtMillis,
                completed = false,
                createdAtMillis = index.toLong(),
                reminderRepeat = companionTodoRepeats.getOrNull(index)
                    ?.let { name -> ReminderRepeat.entries.firstOrNull { it.name == name } }
                    ?: ReminderRepeat.NONE,
            )
        }
    }
}

internal fun activeXHighAlarmFrom(
    items: List<TodoItem>,
    firedDueAtMillis: Long,
    companionShortItems: List<TodoItem>,
    updatedAtMillis: Long,
): ActiveXHighAlarm? {
    val displayItems = items.filter { it.id.isNotBlank() }
    if (displayItems.isEmpty() || firedDueAtMillis <= 0L || updatedAtMillis <= 0L) return null

    return ActiveXHighAlarm(
        todoIds = displayItems.map { it.id },
        todoTitles = displayItems.map { it.title.takeIf { title -> title.isNotBlank() } ?: "Todo due" },
        firedDueAtMillis = firedDueAtMillis,
        companionTodoIds = companionShortItems.map { it.id },
        companionTodoTitles = companionShortItems.map { it.title },
        companionTodoPriorities = companionShortItems.map { it.priority.name },
        companionTodoRepeats = companionShortItems.map { it.reminderRepeat.name },
        updatedAtMillis = updatedAtMillis,
    )
}

internal object ActiveXHighAlarmCodec {
    private const val Version = "1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(alarm: ActiveXHighAlarm): String {
        return listOf(
            Version,
            alarm.firedDueAtMillis.toString(),
            alarm.updatedAtMillis.toString(),
            encodeList(alarm.todoIds),
            encodeList(alarm.todoTitles),
            encodeList(alarm.companionTodoIds),
            encodeList(alarm.companionTodoTitles),
            encodeList(alarm.companionTodoPriorities),
            encodeList(alarm.companionTodoRepeats),
        ).joinToString("\n")
    }

    fun decode(payload: String): ActiveXHighAlarm? {
        return runCatching {
            val parts = payload.split('\n')
            if (parts.size != 9 || parts[0] != Version) return null
            ActiveXHighAlarm(
                firedDueAtMillis = parts[1].toLong(),
                updatedAtMillis = parts[2].toLong(),
                todoIds = decodeList(parts[3]),
                todoTitles = decodeList(parts[4]),
                companionTodoIds = decodeList(parts[5]),
                companionTodoTitles = decodeList(parts[6]),
                companionTodoPriorities = decodeList(parts[7]),
                companionTodoRepeats = decodeList(parts[8]),
            ).normalizedOrNull()
        }.getOrNull()
    }

    private fun encodeList(values: List<String>): String {
        return values.joinToString(",") { value ->
            encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun decodeList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(',').map { token ->
            String(decoder.decode(token), StandardCharsets.UTF_8)
        }
    }

    private fun ActiveXHighAlarm.normalizedOrNull(): ActiveXHighAlarm? {
        if (firedDueAtMillis <= 0L || updatedAtMillis <= 0L) return null
        val displayRows = todoIds.mapIndexedNotNull { index, id ->
            val cleanId = id.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            cleanId to (todoTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Todo due")
        }
        if (displayRows.isEmpty()) return null

        val companionRows = companionTodoIds.mapIndexedNotNull { index, id ->
            val cleanId = id.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            CompanionRow(
                id = cleanId,
                title = companionTodoTitles.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Todo due",
                priority = companionTodoPriorities.getOrNull(index)?.takeIf { it.isNotBlank() }
                    ?: TodoPriority.MEDIUM.name,
                repeat = companionTodoRepeats.getOrNull(index)?.takeIf { it.isNotBlank() }
                    ?: ReminderRepeat.NONE.name,
            )
        }

        return copy(
            todoIds = displayRows.map { it.first },
            todoTitles = displayRows.map { it.second },
            companionTodoIds = companionRows.map { it.id },
            companionTodoTitles = companionRows.map { it.title },
            companionTodoPriorities = companionRows.map { it.priority },
            companionTodoRepeats = companionRows.map { it.repeat },
        )
    }

    private data class CompanionRow(
        val id: String,
        val title: String,
        val priority: String,
        val repeat: String,
    )
}
