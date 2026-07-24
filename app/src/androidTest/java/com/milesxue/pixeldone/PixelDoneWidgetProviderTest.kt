package com.milesxue.pixeldone

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.milesxue.pixeldone.widget.PixelDoneWidgetConfigurationActivity
import com.milesxue.pixeldone.widget.PixelDoneWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelDoneWidgetProviderTest {
    @Test
    fun widgetProviderUsesRealPreviewAndOptionalConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AppWidgetManager.getInstance(context)
            .installedProviders
            .firstOrNull { it.provider.className == PixelDoneWidgetReceiver::class.java.name }

        assertNotNull(provider)
        requireNotNull(provider)
        assertEquals(R.layout.pixeldone_widget_initial, provider.initialLayout)
        assertEquals(R.drawable.pixeldone_widget_preview_image, provider.previewImage)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals(R.layout.pixeldone_widget_preview, provider.previewLayout)
            assertEquals(
                PixelDoneWidgetConfigurationActivity::class.java.name,
                provider.configure?.className,
            )
            assertTrue(
                provider.widgetFeatures and
                    AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0,
            )
            assertTrue(
                provider.widgetFeatures and
                    AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0,
            )
        }
    }
}
