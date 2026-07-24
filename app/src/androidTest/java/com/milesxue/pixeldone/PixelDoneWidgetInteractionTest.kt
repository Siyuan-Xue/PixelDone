package com.milesxue.pixeldone

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milesxue.pixeldone.widget.PixelDoneWidgetConfigStore
import com.milesxue.pixeldone.widget.PixelDoneWidgetConfigurationActivity
import com.milesxue.pixeldone.widget.widgetConfigurationIntent
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
    fun cleanUp() {
        widgetIds.forEach { PixelDoneWidgetConfigStore.remove(context, it) }
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
    fun widgetChecklistSelectionIsCommittedBeforeRefresh() {
        assertNull(PixelDoneWidgetConfigStore.checklistId(context, widgetIds[0]))

        assertTrue(
            PixelDoneWidgetConfigStore.saveChecklistId(
                context = context,
                appWidgetId = widgetIds[0],
                checklistId = "paper",
            ),
        )

        assertEquals(
            "paper",
            PixelDoneWidgetConfigStore.checklistId(context, widgetIds[0]),
        )
    }
}
