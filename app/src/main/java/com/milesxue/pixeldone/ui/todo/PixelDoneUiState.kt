package com.milesxue.pixeldone.ui.todo

import com.milesxue.pixeldone.domain.todo.SortMode
import com.milesxue.pixeldone.domain.todo.TodoChecklistState

/**
 * PixelDone 屏幕状态。
 *
 * 教学说明：现代 Android 推荐 UI 渲染“不可变状态”，用户操作再通过 action 回到 ViewModel。
 * 这个 data class 只描述屏幕需要知道的事实，不负责读写磁盘或调用 Android 系统服务。
 */
data class PixelDoneUiState(
    val checklistState: TodoChecklistState,
    val sortMode: SortMode = SortMode.PRIORITY,
    val hideCompleted: Boolean = false,
    val showDeadlineCountdown: Boolean = false,
    val pendingSystemAction: PendingSystemAction? = null,
)

/**
 * 一次性系统动作。
 *
 * 教学说明：请求权限、打开设置页、打开安装界面这类动作不能直接存在普通 state 中反复执行。
 * 因此用明确类型表达“下一次 UI 需要消费的外部动作”，消费后由 ViewModel 清空。
 */
sealed interface PendingSystemAction {
    data object RequestNotificationPermission : PendingSystemAction
    data class OpenExactAlarmSettings(val todoId: String) : PendingSystemAction
    data class OpenFullScreenIntentSettings(val todoId: String) : PendingSystemAction
    data class OpenUpdateInstaller(val downloadId: Long) : PendingSystemAction
}

sealed interface PixelDoneAction {
    data class ReplaceChecklistState(val state: TodoChecklistState) : PixelDoneAction
    data class SetSortMode(val sortMode: SortMode) : PixelDoneAction
    data class SetHideCompleted(val hideCompleted: Boolean) : PixelDoneAction
    data class SetDeadlineCountdownVisible(val visible: Boolean) : PixelDoneAction
    data object SystemActionConsumed : PixelDoneAction
}
