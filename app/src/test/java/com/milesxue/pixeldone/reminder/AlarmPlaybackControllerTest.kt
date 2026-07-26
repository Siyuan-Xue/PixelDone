package com.milesxue.pixeldone.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPlaybackControllerTest {
    @Test
    fun preparedPlayerStartsWhileVibrationStartsImmediately() {
        val player = FakeAlarmPlayer()
        val vibration = FakeVibrationController()
        val scheduler = FakeTimeoutScheduler()
        val controller = controller(player, vibration, scheduler)

        controller.start {}

        assertEquals(1, vibration.startCount)
        assertTrue(player.prepareAsyncCalled)
        assertFalse(player.started)

        player.firePrepared()

        assertTrue(player.started)
    }

    @Test
    fun stopDuringPrepareReleasesWithoutStartingFromLateCallback() {
        val player = FakeAlarmPlayer()
        val vibration = FakeVibrationController()
        val scheduler = FakeTimeoutScheduler()
        val controller = controller(player, vibration, scheduler)

        controller.start {}
        controller.stop()
        player.fireLatePrepared()

        assertTrue(player.released)
        assertFalse(player.started)
        assertEquals(1, vibration.cancelCount)
        assertTrue(scheduler.tasks.single().cancelled)
    }

    @Test
    fun errorDuringPrepareReleasesAudioButKeepsSafetyTimeout() {
        val player = FakeAlarmPlayer()
        val vibration = FakeVibrationController()
        val scheduler = FakeTimeoutScheduler()
        val controller = controller(player, vibration, scheduler)

        controller.start {}
        player.fireError()

        assertTrue(player.released)
        assertEquals(0, vibration.cancelCount)
        assertFalse(scheduler.tasks.single().cancelled)
    }

    @Test
    fun hardTimeoutStopsAllResourcesAndInvokesCallbackOnce() {
        val player = FakeAlarmPlayer()
        val vibration = FakeVibrationController()
        val scheduler = FakeTimeoutScheduler()
        val controller = controller(player, vibration, scheduler)
        var timeoutCount = 0

        controller.start { timeoutCount += 1 }
        scheduler.fire(0)
        scheduler.fire(0)

        assertEquals(1, timeoutCount)
        assertTrue(player.released)
        assertEquals(1, vibration.cancelCount)
    }

    @Test
    fun restartingInvalidatesCallbacksFromPreviousPlayer() {
        val first = FakeAlarmPlayer()
        val second = FakeAlarmPlayer()
        val players = ArrayDeque(listOf(first, second))
        val vibration = FakeVibrationController()
        val scheduler = FakeTimeoutScheduler()
        val controller = AlarmPlaybackController(
            playerFactory = { players.removeFirst() },
            vibrationController = vibration,
            timeoutScheduler = scheduler,
            hardTimeoutMillis = 600_000L,
        )

        controller.start {}
        controller.start {}
        first.fireLatePrepared()
        second.firePrepared()

        assertFalse(first.started)
        assertTrue(first.released)
        assertTrue(second.started)
        assertTrue(scheduler.tasks.first().cancelled)
    }

    @Test
    fun timeoutDuringPlayerCreationRejectsLatePlayerWithoutPreparingIt() {
        val player = FakeAlarmPlayer()
        val vibration = FakeVibrationController()
        val scheduler = FakeTimeoutScheduler()
        var timeoutCount = 0
        val controller = AlarmPlaybackController(
            playerFactory = {
                scheduler.fire(0)
                player
            },
            vibrationController = vibration,
            timeoutScheduler = scheduler,
            hardTimeoutMillis = 600_000L,
        )

        controller.start { timeoutCount += 1 }

        assertEquals(1, timeoutCount)
        assertEquals(1, vibration.cancelCount)
        assertTrue(player.released)
        assertFalse(player.prepareAsyncCalled)
        assertFalse(player.started)
    }

    private fun controller(
        player: FakeAlarmPlayer,
        vibration: FakeVibrationController,
        scheduler: FakeTimeoutScheduler,
    ) = AlarmPlaybackController(
        playerFactory = { player },
        vibrationController = vibration,
        timeoutScheduler = scheduler,
        hardTimeoutMillis = 600_000L,
    )

    private class FakeAlarmPlayer : AlarmAudioPlayer {
        private var onPrepared: (() -> Unit)? = null
        private var onError: (() -> Unit)? = null
        private var lastOnPrepared: (() -> Unit)? = null
        var prepareAsyncCalled = false
        var started = false
        var released = false

        override fun setCallbacks(
            onPrepared: () -> Unit,
            onError: () -> Unit,
        ) {
            this.onPrepared = onPrepared
            this.onError = onError
            lastOnPrepared = onPrepared
        }

        override fun clearCallbacks() {
            onPrepared = null
            onError = null
        }

        override fun prepareAsync() {
            prepareAsyncCalled = true
        }

        override fun start() {
            started = true
        }

        override fun release() {
            released = true
        }

        fun firePrepared() {
            onPrepared?.invoke()
        }

        fun fireLatePrepared() {
            lastOnPrepared?.invoke()
        }

        fun fireError() {
            onError?.invoke()
        }
    }

    private class FakeVibrationController : AlarmVibrationController {
        var startCount = 0
        var cancelCount = 0

        override fun start() {
            startCount += 1
        }

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakeTimeoutScheduler : AlarmTimeoutScheduler {
        val tasks = mutableListOf<Task>()

        override fun schedule(delayMillis: Long, action: () -> Unit): AlarmTimeoutHandle {
            val task = Task(delayMillis, action)
            tasks += task
            return AlarmTimeoutHandle { task.cancelled = true }
        }

        fun fire(index: Int) {
            val task = tasks[index]
            if (!task.cancelled) {
                task.action()
            }
        }

        data class Task(
            val delayMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )
    }
}
