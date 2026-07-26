package com.milesxue.pixeldone.widget

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps widget completion haptics independent from the completion transaction.
 *
 * Touch vibration is intentionally limited to APIs with the predefined click effect. Older
 * devices stay silent instead of falling back to a buzzy one-shot vibration.
 */
internal class WidgetCompletionHaptics(
    private val platform: WidgetCompletionHapticsPlatform,
    private val dispatcher: CoroutineDispatcher,
) {
    constructor(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        platform = AndroidWidgetCompletionHapticsPlatform(context.applicationContext),
        dispatcher = dispatcher,
    )

    suspend fun perform() {
        withContext(dispatcher) {
            runCatching {
                val sdkInt = platform.sdkInt
                if (sdkInt < Build.VERSION_CODES.Q || !platform.hasVibrator()) {
                    return@runCatching
                }
                if (
                    sdkInt < Build.VERSION_CODES.TIRAMISU &&
                    !platform.isHapticFeedbackEnabled()
                ) {
                    return@runCatching
                }

                platform.vibratePredefinedClick(
                    useTouchAttributes = sdkInt >= Build.VERSION_CODES.TIRAMISU,
                )
            }
        }
    }
}

internal interface WidgetCompletionHapticsPlatform {
    val sdkInt: Int

    fun hasVibrator(): Boolean

    fun isHapticFeedbackEnabled(): Boolean

    fun vibratePredefinedClick(useTouchAttributes: Boolean)
}

private class AndroidWidgetCompletionHapticsPlatform(
    private val context: Context,
) : WidgetCompletionHapticsPlatform {
    override val sdkInt: Int
        get() = Build.VERSION.SDK_INT

    private val vibrator: Vibrator? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    @Suppress("DEPRECATION")
    override fun isHapticFeedbackEnabled(): Boolean =
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) != 0

    override fun vibratePredefinedClick(useTouchAttributes: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val target = vibrator ?: return
        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        if (useTouchAttributes && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.vibrate(
                effect,
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build(),
            )
        } else {
            target.vibrate(effect)
        }
    }
}
