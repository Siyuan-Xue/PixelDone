package com.milesxue.pixeldone.widget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetCompletionHapticsTest {
    @Test
    fun api33UsesOneTouchAttributedClick() = runTest {
        val platform = FakePlatform(
            sdkInt = 33,
            vibratorAvailable = true,
            hapticFeedbackEnabled = false,
        )

        haptics(platform).perform()

        assertEquals(1, platform.hasVibratorCalls)
        assertEquals(0, platform.hapticSettingCalls)
        assertEquals(1, platform.vibrationCalls)
        assertTrue(platform.lastUsedTouchAttributes)
    }

    @Test
    fun api29To32ObeysDisabledHapticSetting() = runTest {
        val platform = FakePlatform(
            sdkInt = 32,
            vibratorAvailable = true,
            hapticFeedbackEnabled = false,
        )

        haptics(platform).perform()

        assertEquals(1, platform.hasVibratorCalls)
        assertEquals(1, platform.hapticSettingCalls)
        assertEquals(0, platform.vibrationCalls)
    }

    @Test
    fun api29To32UsesOneUnattributedClickWhenEnabled() = runTest {
        val platform = FakePlatform(
            sdkInt = 29,
            vibratorAvailable = true,
            hapticFeedbackEnabled = true,
        )

        haptics(platform).perform()

        assertEquals(1, platform.hasVibratorCalls)
        assertEquals(1, platform.hapticSettingCalls)
        assertEquals(1, platform.vibrationCalls)
        assertFalse(platform.lastUsedTouchAttributes)
    }

    @Test
    fun unavailableVibratorSkipsSettingAndVibration() = runTest {
        val platform = FakePlatform(
            sdkInt = 33,
            vibratorAvailable = false,
            hapticFeedbackEnabled = true,
        )

        haptics(platform).perform()

        assertEquals(1, platform.hasVibratorCalls)
        assertEquals(0, platform.hapticSettingCalls)
        assertEquals(0, platform.vibrationCalls)
    }

    @Test
    fun api26To28StaySilentWithoutTouchingPlatformVibrator() = runTest {
        val platform = FakePlatform(
            sdkInt = 28,
            vibratorAvailable = true,
            hapticFeedbackEnabled = true,
        )

        haptics(platform).perform()

        assertEquals(0, platform.hasVibratorCalls)
        assertEquals(0, platform.hapticSettingCalls)
        assertEquals(0, platform.vibrationCalls)
    }

    private fun TestScope.haptics(platform: WidgetCompletionHapticsPlatform) =
        WidgetCompletionHaptics(
            platform = platform,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    private class FakePlatform(
        override val sdkInt: Int,
        private val vibratorAvailable: Boolean,
        private val hapticFeedbackEnabled: Boolean,
    ) : WidgetCompletionHapticsPlatform {
        var hasVibratorCalls = 0
        var hapticSettingCalls = 0
        var vibrationCalls = 0
        var lastUsedTouchAttributes = false

        override fun hasVibrator(): Boolean {
            hasVibratorCalls += 1
            return vibratorAvailable
        }

        override fun isHapticFeedbackEnabled(): Boolean {
            hapticSettingCalls += 1
            return hapticFeedbackEnabled
        }

        override fun vibratePredefinedClick(useTouchAttributes: Boolean) {
            vibrationCalls += 1
            lastUsedTouchAttributes = useTouchAttributes
        }
    }
}
