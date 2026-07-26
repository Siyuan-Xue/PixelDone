package com.milesxue.pixeldone.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class AndroidAlarmAudioPlayer(
    context: Context,
    soundUri: Uri,
) : AlarmAudioPlayer {
    private val mediaPlayer = MediaPlayer()

    init {
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mediaPlayer.setDataSource(context, soundUri)
            mediaPlayer.isLooping = true
        } catch (error: Exception) {
            mediaPlayer.release()
            throw error
        }
    }

    override fun setCallbacks(
        onPrepared: () -> Unit,
        onError: () -> Unit,
    ) {
        mediaPlayer.setOnPreparedListener { onPrepared() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            onError()
            true
        }
    }

    override fun clearCallbacks() {
        mediaPlayer.setOnPreparedListener(null)
        mediaPlayer.setOnErrorListener(null)
    }

    override fun prepareAsync() {
        mediaPlayer.prepareAsync()
    }

    override fun start() {
        mediaPlayer.start()
    }

    override fun release() {
        mediaPlayer.release()
    }
}

internal class AndroidAlarmVibrationController(
    private val vibrator: Vibrator,
    private val pattern: LongArray,
) : AlarmVibrationController {
    override fun start() {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    override fun cancel() {
        vibrator.cancel()
    }
}

internal object WatchdogAlarmTimeoutScheduler : AlarmTimeoutScheduler {
    private val executor = ScheduledThreadPoolExecutor(
        1,
    ) { runnable ->
        Thread(runnable, "PixelDone-XHigh-Watchdog").apply {
            isDaemon = true
        }
    }.apply {
        removeOnCancelPolicy = true
    }

    override fun schedule(delayMillis: Long, action: () -> Unit): AlarmTimeoutHandle {
        val future = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
        return AlarmTimeoutHandle { future.cancel(false) }
    }
}
