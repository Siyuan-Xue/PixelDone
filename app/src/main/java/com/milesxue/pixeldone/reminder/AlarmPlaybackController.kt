package com.milesxue.pixeldone.reminder

internal interface AlarmAudioPlayer {
    fun setCallbacks(
        onPrepared: () -> Unit,
        onError: () -> Unit,
    )

    fun clearCallbacks()
    fun prepareAsync()
    fun start()
    fun release()
}

internal interface AlarmVibrationController {
    fun start()
    fun cancel()
}

internal fun interface AlarmTimeoutHandle {
    fun cancel()
}

internal fun interface AlarmTimeoutScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AlarmTimeoutHandle
}

/**
 * Owns one alarm playback session at a time.
 *
 * MediaPlayer can remain in PREPARING after the user has already stopped the alarm. The generation
 * check makes every callback from that retired player a no-op, while release() remains safe from
 * PREPARING and ERROR without querying isPlaying() or calling stop().
 */
internal class AlarmPlaybackController(
    private val playerFactory: () -> AlarmAudioPlayer?,
    private val vibrationController: AlarmVibrationController,
    private val timeoutScheduler: AlarmTimeoutScheduler,
    private val hardTimeoutMillis: Long,
) {
    private val lock = Any()
    private var generation = 0L
    private var player: AlarmAudioPlayer? = null
    private var timeoutHandle: AlarmTimeoutHandle? = null
    private var sessionActive = false

    fun start(onHardTimeout: () -> Unit) {
        stop()
        val sessionGeneration = synchronized(lock) {
            sessionActive = true
            generation
        }

        runCatching(vibrationController::start)
        if (!isCurrentSession(sessionGeneration)) {
            runCatching(vibrationController::cancel)
            return
        }

        val scheduledTimeout = timeoutScheduler.schedule(hardTimeoutMillis) {
            if (stopSession(expectedGeneration = sessionGeneration)) {
                onHardTimeout()
            }
        }
        val keepTimeout = synchronized(lock) {
            if (sessionGeneration == generation && sessionActive) {
                timeoutHandle = scheduledTimeout
                true
            } else {
                false
            }
        }
        if (!keepTimeout) {
            scheduledTimeout.cancel()
        }

        val newPlayer = runCatching(playerFactory).getOrNull() ?: return
        val accepted = synchronized(lock) {
            if (sessionGeneration == generation && sessionActive) {
                player = newPlayer
                true
            } else {
                false
            }
        }
        if (!accepted) {
            disposePlayer(newPlayer)
            return
        }

        runCatching {
            newPlayer.setCallbacks(
                onPrepared = {
                    if (!isCurrentPlayer(sessionGeneration, newPlayer)) return@setCallbacks
                    runCatching(newPlayer::start)
                        .onFailure { releaseCurrentPlayer(newPlayer) }
                },
                onError = {
                    if (isCurrentPlayer(sessionGeneration, newPlayer)) {
                        releaseCurrentPlayer(newPlayer)
                    }
                },
            )
            if (!isCurrentPlayer(sessionGeneration, newPlayer)) {
                return@runCatching
            }
            newPlayer.prepareAsync()
        }.onFailure {
            releaseCurrentPlayer(newPlayer)
        }
    }

    fun stop() {
        stopSession(expectedGeneration = null)
    }

    private fun stopSession(expectedGeneration: Long?): Boolean {
        val retired = synchronized(lock) {
            if (
                expectedGeneration != null &&
                (generation != expectedGeneration || !sessionActive)
            ) {
                return false
            }
            val resources = RetiredSession(
                player = player,
                timeoutHandle = timeoutHandle,
                wasActive = sessionActive,
            )
            generation += 1L
            sessionActive = false
            player = null
            timeoutHandle = null
            resources
        }
        retired.timeoutHandle?.cancel()
        retired.player?.let(::disposePlayer)
        if (retired.wasActive) {
            runCatching(vibrationController::cancel)
        }
        return retired.wasActive
    }

    private fun isCurrentSession(sessionGeneration: Long): Boolean = synchronized(lock) {
        generation == sessionGeneration && sessionActive
    }

    private fun isCurrentPlayer(
        sessionGeneration: Long,
        target: AlarmAudioPlayer,
    ): Boolean = synchronized(lock) {
        generation == sessionGeneration && sessionActive && player === target
    }

    private fun releaseCurrentPlayer(target: AlarmAudioPlayer) {
        val shouldRelease = synchronized(lock) {
            if (player === target) {
                player = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) disposePlayer(target)
    }

    private fun disposePlayer(target: AlarmAudioPlayer) {
        runCatching(target::clearCallbacks)
        runCatching(target::release)
    }

    private data class RetiredSession(
        val player: AlarmAudioPlayer?,
        val timeoutHandle: AlarmTimeoutHandle?,
        val wasActive: Boolean,
    )
}
