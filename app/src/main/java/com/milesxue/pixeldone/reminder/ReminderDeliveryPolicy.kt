package com.milesxue.pixeldone.reminder

internal data class ReminderDeliveryCapabilities(
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val shortNotificationChannelEnabled: Boolean,
    val xhighChannelCanInterrupt: Boolean,
    val exactAlarmAccess: Boolean,
    val fullScreenIntentAccess: Boolean,
) {
    val canShowShortNotification: Boolean
        get() = notificationPermissionGranted &&
            notificationsEnabled &&
            shortNotificationChannelEnabled
}

internal enum class XHighReminderDelivery {
    FULLSCREEN_LOOP,
    SHORT_NOTIFICATION,
    SUPPRESSED,
}

internal fun xhighReminderDelivery(
    capabilities: ReminderDeliveryCapabilities,
): XHighReminderDelivery {
    val canShowControllableFullscreenAlarm =
        capabilities.notificationPermissionGranted &&
            capabilities.notificationsEnabled &&
            capabilities.xhighChannelCanInterrupt &&
            capabilities.exactAlarmAccess &&
            capabilities.fullScreenIntentAccess

    return when {
        canShowControllableFullscreenAlarm -> XHighReminderDelivery.FULLSCREEN_LOOP
        capabilities.canShowShortNotification -> XHighReminderDelivery.SHORT_NOTIFICATION
        else -> XHighReminderDelivery.SUPPRESSED
    }
}
