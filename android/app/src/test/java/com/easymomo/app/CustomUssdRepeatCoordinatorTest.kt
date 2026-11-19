package com.easymomo.app

import com.easymomo.app.ussd.CustomUssdRepeatCoordinator
import com.easymomo.app.ussd.UssdPromptObserver
import com.easymomo.app.ussd.capture.CustomUssdOperationType
import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomUssdRepeatCoordinatorTest {

    private fun sampleFlow(id: String = "flow-1") = SimpleCustomUssdFlow(
        id = id,
        name = "Repeat Airtime",
        operationType = CustomUssdOperationType.STEP_BY_STEP,
        steps = listOf("*171#", "1", "<phoneNumber>", "<amount>"),
        oneTimeCode = "",
        simSlotIndex = 0,
        formulaPreview = "*171# , 1 , <phoneNumber> , <amount>"
    )

    @Test
    fun `clampRepeatCount clamps out-of-range values to 1 through 20`() {
        assertEquals(1, CustomUssdRepeatCoordinator.clampRepeatCount(0))
        assertEquals(1, CustomUssdRepeatCoordinator.clampRepeatCount(-5))
        assertEquals(1, CustomUssdRepeatCoordinator.clampRepeatCount(1))
        assertEquals(3, CustomUssdRepeatCoordinator.clampRepeatCount(3))
        assertEquals(20, CustomUssdRepeatCoordinator.clampRepeatCount(20))
        assertEquals(20, CustomUssdRepeatCoordinator.clampRepeatCount(99))
    }

    @Test
    fun `3-run batch lifecycle progresses through awaiting close, countdown, cancellation, and completion`() {
        val coordinator = CustomUssdRepeatCoordinator()
        val flow = sampleFlow()

        val run1 = coordinator.startBatch(flow, 3)
        assertEquals(3, run1.totalRuns)
        assertEquals(1, run1.currentRun)
        assertEquals(2, run1.nextRunNumber)
        assertTrue(run1.isBatch)
        assertTrue(run1.hasMoreRuns)
        assertFalse(run1.awaitingDialogClose)
        assertNull(run1.countdownSeconds)
        assertFalse(run1.pausedOnError)

        // Calling setCountdown before awaitingDialogClose must leave state unchanged
        val ignoredCountdown = coordinator.setCountdown(2)
        assertNotNull(ignoredCountdown)
        assertNull(ignoredCountdown?.countdownSeconds)

        val awaiting1 = coordinator.markRunAwaitingClose()
        assertNotNull(awaiting1)
        assertTrue(awaiting1!!.awaitingDialogClose)
        assertFalse(awaiting1.pausedOnError)
        assertNull(awaiting1.countdownSeconds)

        val countdown2 = coordinator.setCountdown(2)
        assertEquals(2, countdown2?.countdownSeconds)

        val cancelled = coordinator.cancelCountdown()
        assertNotNull(cancelled)
        assertNull(cancelled?.countdownSeconds)
        assertTrue(cancelled!!.awaitingDialogClose)

        val run2 = coordinator.advanceToNextRun()
        assertNotNull(run2)
        assertEquals(2, run2!!.currentRun)
        assertEquals(3, run2.nextRunNumber)
        assertTrue(run2.hasMoreRuns)
        assertFalse(run2.awaitingDialogClose)
        assertNull(run2.countdownSeconds)
        assertFalse(run2.pausedOnError)

        val awaiting2 = coordinator.markRunAwaitingClose()
        assertNotNull(awaiting2)
        assertTrue(awaiting2!!.awaitingDialogClose)

        val run3 = coordinator.advanceToNextRun()
        assertNotNull(run3)
        assertEquals(3, run3!!.currentRun)
        assertEquals(3, run3.nextRunNumber)
        assertFalse(run3.hasMoreRuns)
        assertFalse(run3.awaitingDialogClose)

        // Final run has no more runs, so markRunAwaitingClose clears the batch
        assertNull(coordinator.markRunAwaitingClose())
        assertNull(coordinator.state.value)
    }

    @Test
    fun `1-run lifecycle marks single non-batch run and clears on completion`() {
        val coordinator = CustomUssdRepeatCoordinator()
        val run1 = coordinator.startBatch(sampleFlow(), 1)

        assertEquals(1, run1.totalRuns)
        assertEquals(1, run1.currentRun)
        assertEquals(1, run1.nextRunNumber)
        assertFalse(run1.isBatch)
        assertFalse(run1.hasMoreRuns)

        assertNull(coordinator.markRunAwaitingClose())
        assertNull(coordinator.state.value)
        assertNull(coordinator.advanceToNextRun())
    }

    @Test
    fun `markPausedOnError blocks countdown until manually resumed via advanceToNextRun or cleared`() {
        val coordinator = CustomUssdRepeatCoordinator()
        coordinator.startBatch(sampleFlow(), 3)

        coordinator.markRunAwaitingClose()
        coordinator.setCountdown(2)

        val paused = coordinator.markPausedOnError()
        assertNotNull(paused)
        assertTrue(paused!!.pausedOnError)
        assertFalse(paused.awaitingDialogClose)
        assertNull(paused.countdownSeconds)

        // Countdown must not start while pausedOnError is true
        val stillPaused = coordinator.setCountdown(1)
        assertTrue(stillPaused!!.pausedOnError)
        assertNull(stillPaused.countdownSeconds)

        // Manual "Run Next" resumes to run 2 and clears pausedOnError
        val resumed = coordinator.advanceToNextRun()
        assertNotNull(resumed)
        assertEquals(2, resumed!!.currentRun)
        assertFalse(resumed.pausedOnError)
        assertFalse(resumed.awaitingDialogClose)
        assertNull(resumed.countdownSeconds)

        coordinator.clear()
        assertNull(coordinator.state.value)
        assertNull(coordinator.markPausedOnError())
        assertNull(coordinator.cancelCountdown())
    }

    @Test
    fun `UssdPromptObserver emits window OPENED and CLOSED events`() = runBlocking {
        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            UssdPromptObserver.windowEvents.take(2).toList()
        }

        UssdPromptObserver.onUssdWindowOpened()
        UssdPromptObserver.onUssdWindowClosed()

        assertEquals(
            listOf(
                UssdPromptObserver.UssdWindowEvent.OPENED,
                UssdPromptObserver.UssdWindowEvent.CLOSED
            ),
            collected.await()
        )
    }
}
