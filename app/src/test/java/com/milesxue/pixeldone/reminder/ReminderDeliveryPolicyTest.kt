package com.milesxue.pixeldone.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDeliveryPolicyTest {
    private val allCapabilities = ReminderDeliveryCapabilities(
        notificationPermissionGranted = true,
        notificationsEnabled = true,
        shortNotificationChannelEnabled = true,
        xhighChannelCanInterrupt = true,
        exactAlarmAccess = true,
        fullScreenIntentAccess = true,
    )

    @Test
    fun allCapabilitiesAllowControllableFullscreenLoop() {
        assertEquals(
            XHighReminderDelivery.FULLSCREEN_LOOP,
            xhighReminderDelivery(allCapabilities),
        )
    }

    @Test
    fun missingFullscreenRequirementFallsBackToShortNotification() {
        listOf(
            allCapabilities.copy(xhighChannelCanInterrupt = false),
            allCapabilities.copy(exactAlarmAccess = false),
            allCapabilities.copy(fullScreenIntentAccess = false),
        ).forEach { capabilities ->
            assertEquals(
                XHighReminderDelivery.SHORT_NOTIFICATION,
                xhighReminderDelivery(capabilities),
            )
        }
    }

    @Test
    fun missingVisibleNotificationPathSuppressesUncontrolledAudio() {
        listOf(
            allCapabilities.copy(notificationPermissionGranted = false),
            allCapabilities.copy(notificationsEnabled = false),
            allCapabilities.copy(
                fullScreenIntentAccess = false,
                shortNotificationChannelEnabled = false,
            ),
        ).forEach { capabilities ->
            assertEquals(
                XHighReminderDelivery.SUPPRESSED,
                xhighReminderDelivery(capabilities),
            )
        }
    }
}
