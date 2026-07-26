package com.milesxue.pixeldone.widget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WidgetCompletionFeedbackCleanupWorkerTest {
    @Test
    fun cleanupDelayNeverRunsBeforeTheFeedbackExpiry() {
        assertEquals(
            2_000L,
            widgetCompletionFeedbackDelayMillis(
                expiresAtMillis = 12_000L,
                nowMillis = 10_000L,
            ),
        )
        assertEquals(
            0L,
            widgetCompletionFeedbackDelayMillis(
                expiresAtMillis = 9_999L,
                nowMillis = 10_000L,
            ),
        )
    }

    @Test
    fun finalExpiryPreservesTwoVisibleSecondsWithoutRacingSlowPersistence() {
        assertEquals(
            3_000L,
            widgetCompletionFeedbackFinalExpiryMillis(
                visibleUntilMillis = 3_000L,
                completionFinishedAtMillis = 1_500L,
            ),
        )
        assertEquals(
            3_501L,
            widgetCompletionFeedbackFinalExpiryMillis(
                visibleUntilMillis = 3_000L,
                completionFinishedAtMillis = 3_500L,
            ),
        )
    }

    @Test
    fun cleanupWorkIsUniquePerWidgetAndFeedbackToken() {
        val first = widgetCompletionFeedbackWorkName(appWidgetId = 42, token = "first")
        assertEquals(first, widgetCompletionFeedbackWorkName(appWidgetId = 42, token = "first"))
        assertNotEquals(first, widgetCompletionFeedbackWorkName(appWidgetId = 42, token = "second"))
        assertNotEquals(first, widgetCompletionFeedbackWorkName(appWidgetId = 43, token = "first"))
    }

    @Test
    fun cleanupPublishesEvenWhenTheFeedbackStateWasAlreadyRemoved() = runBlocking {
        var removeCalls = 0
        var updateCalls = 0

        runWidgetCompletionFeedbackCleanup(
            removeExpiredState = { removeCalls += 1 },
            updateWidget = { updateCalls += 1 },
        )

        assertEquals(1, removeCalls)
        assertEquals(1, updateCalls)
    }

    @Test
    fun cleanupPropagatesPublicationFailureSoTheWorkerCanRetry() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runWidgetCompletionFeedbackCleanup(
                    removeExpiredState = {},
                    updateWidget = { error("publish failed") },
                )
            }
        }
    }
}
