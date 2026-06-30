package com.milesxue.pixeldone.domain.todo

/**
 * 领域模型：只描述 PixelDone 的核心数据结构，不依赖 Android 或 Compose。
 * 初学者阅读时可以先从这里理解“任务、清单、优先级、提醒方式”这些业务名词。
 */


data class TodoItem(
    val id: String,
    val title: String,
    val priority: TodoPriority,
    val dueAtMillis: Long,
    val completed: Boolean,
    val createdAtMillis: Long,
    val reminderRepeat: ReminderRepeat = ReminderRepeat.NONE,
    val imageFileName: String? = null,
    val trashedFromChecklistId: String? = null,
    val trashedFromChecklistName: String? = null,
    val trashedAtMillis: Long? = null,
)

data class TodoChecklist(
    val id: String,
    val name: String,
    val items: List<TodoItem>,
    val createdAtMillis: Long,
)

data class TodoChecklistState(
    val lists: List<TodoChecklist>,
    val selectedListId: String,
)

enum class TodoPriority {
    XHIGH,
    HIGH,
    MEDIUM,
    LOW,
}

enum class ReminderRepeat {
    NONE,
    DAILY,
    WEEKLY,
}

enum class SortMode {
    PRIORITY,
    TIME,
}

enum class ReminderScheduleMode {
    INEXACT_NOTIFICATION,
    SYSTEM_ALARM,
}

enum class ReminderAlertMode {
    SHORT_NOTIFICATION,
    FULLSCREEN_ALARM,
}

data class ReminderDispatchPlan(
    val fullscreenAlarmItems: List<TodoItem> = emptyList(),
    val shortNotificationItems: List<TodoItem> = emptyList(),
    val rescheduleItems: List<TodoItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = fullscreenAlarmItems.isEmpty() && shortNotificationItems.isEmpty()

    val signatureIds: List<String>
        get() = (fullscreenAlarmItems + shortNotificationItems)
            .map { it.id }
            .distinct()
            .sorted()
}

enum class ReminderCapability {
    NOTIFICATION_PERMISSION,
    EXACT_ALARM_ACCESS,
    FULL_SCREEN_INTENT_ACCESS,
}

object ReminderNotificationIds {
    const val XHIGH_ALARM = 0x30000001
    private const val SHORT_ITEM_NAMESPACE = 0x10000000
    private const val SHORT_BATCH_NAMESPACE = 0x20000000
    private const val SHORT_FOLLOW_UP_NAMESPACE = 0x28000000
    private const val PAYLOAD_MASK = 0x0FFFFFFF
    private const val SHORT_BATCH_PAYLOAD_MASK = 0x07FFFFFF

    fun shortItem(todoId: String): Int {
        return SHORT_ITEM_NAMESPACE or (todoId.hashCode() and PAYLOAD_MASK)
    }

    fun shortBatch(firedDueAtMillis: Long): Int {
        val foldedTime = (firedDueAtMillis xor (firedDueAtMillis ushr 32)).toInt()
        return SHORT_BATCH_NAMESPACE or (foldedTime and SHORT_BATCH_PAYLOAD_MASK)
    }

    fun shortFollowUp(firedDueAtMillis: Long): Int {
        val foldedTime = (firedDueAtMillis xor (firedDueAtMillis ushr 32)).toInt()
        return SHORT_FOLLOW_UP_NAMESPACE or (foldedTime and SHORT_BATCH_PAYLOAD_MASK)
    }
}

internal const val DailyReminderIntervalMillis = 24L * 60L * 60L * 1000L
internal const val WeeklyReminderIntervalMillis = 7L * DailyReminderIntervalMillis
internal const val DefaultSnoozeIntervalMillis = 10L * 60L * 1000L

const val DefaultChecklistId = "main"
const val DefaultChecklistName = "MAIN"
const val TrashChecklistId = "trash"
const val TrashChecklistName = "TRASH"