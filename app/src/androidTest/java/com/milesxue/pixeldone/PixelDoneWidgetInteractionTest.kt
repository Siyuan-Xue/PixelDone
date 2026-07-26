package com.milesxue.pixeldone

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milesxue.pixeldone.widget.PixelDoneWidgetStateStore
import com.milesxue.pixeldone.widget.PixelDoneWidgetConfigStore
import com.milesxue.pixeldone.widget.PixelDoneWidgetConfigurationActivity
import com.milesxue.pixeldone.widget.WidgetCompletionFeedback
import com.milesxue.pixeldone.widget.widgetConfigurationIntent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelDoneWidgetInteractionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val widgetIds = listOf(930_001, 930_002)

    @After
    fun cleanUp() = runBlocking {
        widgetIds.forEach { appWidgetId ->
            PixelDoneWidgetStateStore.clear(context, AppWidgetId(appWidgetId))
            PixelDoneWidgetConfigStore.clearLegacy(context, appWidgetId)
        }
    }

    @Test
    fun configurationIntentsAreUniquePerWidgetInstance() {
        val first = widgetConfigurationIntent(context, widgetIds[0])
        val second = widgetConfigurationIntent(context, widgetIds[1])

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, first.action)
        assertEquals(
            PixelDoneWidgetConfigurationActivity::class.java.name,
            first.component?.className,
        )
        assertEquals(
            widgetIds[0],
            first.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
        assertNotEquals(first.data, second.data)
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS != 0)
        assertEquals(0, first.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    @Test
    fun widgetChecklistSelectionIsCommittedToGlanceState() = runBlocking {
        val glanceId = AppWidgetId(widgetIds[0])
        assertNull(
            PixelDoneWidgetStateStore.selectedChecklistId(
                PixelDoneWidgetStateStore.readPreferences(context, glanceId),
            ),
        )

        PixelDoneWidgetStateStore.replaceConfiguration(
            context = context,
            glanceId = glanceId,
            checklistId = "paper",
        )

        assertEquals(
            "paper",
            PixelDoneWidgetStateStore.selectedChecklistId(
                PixelDoneWidgetStateStore.readPreferences(context, glanceId),
            ),
        )
    }

    @Test
    fun checklistSelectionsRemainIsolatedPerWidget() = runBlocking {
        PixelDoneWidgetStateStore.replaceConfiguration(
            context = context,
            glanceId = AppWidgetId(widgetIds[0]),
            checklistId = "paper",
        )
        PixelDoneWidgetStateStore.replaceConfiguration(
            context = context,
            glanceId = AppWidgetId(widgetIds[1]),
            checklistId = "main",
        )

        assertEquals(
            "paper",
            PixelDoneWidgetStateStore.selectedChecklistId(
                PixelDoneWidgetStateStore.readPreferences(
                    context,
                    AppWidgetId(widgetIds[0]),
                ),
            ),
        )
        assertEquals(
            "main",
            PixelDoneWidgetStateStore.selectedChecklistId(
                PixelDoneWidgetStateStore.readPreferences(
                    context,
                    AppWidgetId(widgetIds[1]),
                ),
            ),
        )
    }

    @Test
    fun newGlanceStateTakesPriorityOverLegacySelection() = runBlocking {
        writeLegacySelection(widgetIds[0], "legacy")
        val glanceId = AppWidgetId(widgetIds[0])
        var preferences = PixelDoneWidgetStateStore.readPreferences(context, glanceId)

        assertEquals(
            "legacy",
            PixelDoneWidgetStateStore.selectedChecklistId(
                context = context,
                appWidgetId = widgetIds[0],
                preferences = preferences,
            ),
        )

        PixelDoneWidgetStateStore.replaceConfiguration(
            context = context,
            glanceId = glanceId,
            checklistId = "paper",
        )
        preferences = PixelDoneWidgetStateStore.readPreferences(context, glanceId)
        assertEquals(
            "paper",
            PixelDoneWidgetStateStore.selectedChecklistId(
                context = context,
                appWidgetId = widgetIds[0],
                preferences = preferences,
            ),
        )
        assertTrue(PixelDoneWidgetConfigStore.clearLegacy(context, widgetIds[0]))
        assertNull(PixelDoneWidgetConfigStore.legacyChecklistId(context, widgetIds[0]))
        assertEquals(
            "paper",
            PixelDoneWidgetStateStore.selectedChecklistId(
                context = context,
                appWidgetId = widgetIds[0],
                preferences = preferences,
            ),
        )
    }

    @Test
    fun replacingConfigurationClearsFeedbackAndCanBeRolledBack() = runBlocking {
        val glanceId = AppWidgetId(widgetIds[0])
        PixelDoneWidgetStateStore.replaceConfiguration(
            context = context,
            glanceId = glanceId,
            checklistId = "paper",
        )
        assertTrue(
            PixelDoneWidgetStateStore.addCompletionFeedback(
                context = context,
                glanceId = glanceId,
                feedback = WidgetCompletionFeedback(
                    checklistId = "paper",
                    todoId = "todo-a",
                    token = "token-a",
                    expiresAtMillis = 3_000L,
                ),
                nowMillis = 1_000L,
            ),
        )
        val previousState = PixelDoneWidgetStateStore.replaceConfiguration(
            context = context,
            glanceId = glanceId,
            checklistId = "main",
        )

        var preferences = PixelDoneWidgetStateStore.readPreferences(context, glanceId)
        assertEquals("main", PixelDoneWidgetStateStore.selectedChecklistId(preferences))
        assertTrue(
            PixelDoneWidgetStateStore.completionFeedback(
                preferences = preferences,
                nowMillis = 1_000L,
            ).isEmpty(),
        )

        PixelDoneWidgetStateStore.restore(
            context = context,
            glanceId = glanceId,
            snapshot = previousState,
        )
        preferences = PixelDoneWidgetStateStore.readPreferences(context, glanceId)
        assertEquals("paper", PixelDoneWidgetStateStore.selectedChecklistId(preferences))
        assertEquals(
            listOf("todo-a"),
            PixelDoneWidgetStateStore.completionFeedback(
                preferences = preferences,
                nowMillis = 1_000L,
            ).map(WidgetCompletionFeedback::todoId),
        )
    }

    @Test
    fun duplicateFeedbackIsIdempotentAndTokenRemovalIsExact() = runBlocking {
        val glanceId = AppWidgetId(widgetIds[0])
        val original = WidgetCompletionFeedback(
            checklistId = "main",
            todoId = "todo-a",
            token = "token-a",
            expiresAtMillis = 3_000L,
        )
        assertTrue(
            PixelDoneWidgetStateStore.addCompletionFeedback(
                context,
                glanceId,
                original,
                nowMillis = 1_000L,
            ),
        )
        assertTrue(
            !PixelDoneWidgetStateStore.addCompletionFeedback(
                context,
                glanceId,
                original.copy(token = "token-b", expiresAtMillis = 5_000L),
                nowMillis = 1_500L,
            ),
        )
        assertTrue(
            !PixelDoneWidgetStateStore.removeCompletionFeedback(
                context,
                glanceId,
                checklistId = "main",
                todoId = "todo-a",
                token = "wrong-token",
                nowMillis = 1_500L,
            ),
        )
        assertTrue(
            PixelDoneWidgetStateStore.removeCompletionFeedback(
                context,
                glanceId,
                checklistId = "main",
                todoId = "todo-a",
                token = "token-a",
                nowMillis = 1_500L,
            ),
        )
        assertTrue(
            PixelDoneWidgetStateStore.completionFeedback(
                PixelDoneWidgetStateStore.readPreferences(context, glanceId),
                nowMillis = 1_500L,
            ).isEmpty(),
        )
    }

    @Test
    fun completionExpiryUpdateRequiresTheExactActiveToken() = runBlocking {
        val glanceId = AppWidgetId(widgetIds[0])
        val provisional = WidgetCompletionFeedback(
            checklistId = "main",
            todoId = "todo-a",
            token = "token-a",
            expiresAtMillis = 30_000L,
        )
        assertTrue(
            PixelDoneWidgetStateStore.addCompletionFeedback(
                context,
                glanceId,
                provisional,
                nowMillis = 1_000L,
            ),
        )

        assertNull(
            PixelDoneWidgetStateStore.updateCompletionFeedbackExpiry(
                context,
                glanceId,
                feedback = provisional.copy(token = "wrong-token"),
                expiresAtMillis = 3_000L,
                nowMillis = 1_000L,
            ),
        )
        val finalized = PixelDoneWidgetStateStore.updateCompletionFeedbackExpiry(
            context,
            glanceId,
            feedback = provisional,
            expiresAtMillis = 3_000L,
            nowMillis = 1_000L,
        )

        assertEquals(3_000L, finalized?.expiresAtMillis)
        assertEquals(
            listOf(finalized),
            PixelDoneWidgetStateStore.completionFeedback(
                PixelDoneWidgetStateStore.readPreferences(context, glanceId),
                nowMillis = 1_000L,
            ),
        )
    }

    @Test
    fun expiryCleanupOnlyRemovesTheMatchingDueToken() = runBlocking {
        val glanceId = AppWidgetId(widgetIds[0])
        val first = WidgetCompletionFeedback(
            checklistId = "main",
            todoId = "todo-a",
            token = "token-a",
            expiresAtMillis = 3_000L,
        )
        val second = WidgetCompletionFeedback(
            checklistId = "main",
            todoId = "todo-b",
            token = "token-b",
            expiresAtMillis = 4_000L,
        )
        assertTrue(
            PixelDoneWidgetStateStore.addCompletionFeedback(
                context,
                glanceId,
                first,
                nowMillis = 1_000L,
            ),
        )
        assertTrue(
            PixelDoneWidgetStateStore.addCompletionFeedback(
                context,
                glanceId,
                second,
                nowMillis = 1_000L,
            ),
        )

        assertTrue(
            !PixelDoneWidgetStateStore.removeExpiredCompletionFeedback(
                context,
                glanceId,
                feedback = first,
                nowMillis = 2_999L,
            ),
        )
        assertTrue(
            !PixelDoneWidgetStateStore.removeExpiredCompletionFeedback(
                context,
                glanceId,
                feedback = first.copy(token = "wrong-token"),
                nowMillis = 3_000L,
            ),
        )
        assertTrue(
            PixelDoneWidgetStateStore.removeExpiredCompletionFeedback(
                context,
                glanceId,
                feedback = first,
                nowMillis = 3_000L,
            ),
        )

        assertEquals(
            listOf(second),
            PixelDoneWidgetStateStore.completionFeedback(
                PixelDoneWidgetStateStore.readPreferences(context, glanceId),
                nowMillis = 3_000L,
            ),
        )
    }

    private fun writeLegacySelection(appWidgetId: Int, checklistId: String) {
        assertTrue(
            context.getSharedPreferences(
                "pixeldone_widget_config",
                Context.MODE_PRIVATE,
            ).edit()
                .putString("checklist_$appWidgetId", checklistId)
                .commit(),
        )
    }
}
