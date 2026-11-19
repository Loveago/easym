package com.easymomo.app.ussd

import com.easymomo.app.ussd.capture.SimpleCustomUssdFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Snapshot of a sequential multi-run ("Repeat Count") custom USSD execution batch.
 */
data class CustomUssdRepeatState(
    val flow: SimpleCustomUssdFlow,
    val totalRuns: Int,
    val currentRun: Int,
    val awaitingDialogClose: Boolean = false,
    val countdownSeconds: Int? = null,
    val pausedOnError: Boolean = false
) {
    val isBatch: Boolean get() = totalRuns > 1
    val hasMoreRuns: Boolean get() = currentRun < totalRuns
    val nextRunNumber: Int get() = (currentRun + 1).coerceAtMost(totalRuns)
}

/**
 * State machine tracking repeat-batch progress for custom USSD flows across consecutive
 * system USSD dialog lifecycles.
 */
class CustomUssdRepeatCoordinator {

    companion object {
        const val MIN_REPEAT_COUNT = 1
        const val MAX_REPEAT_COUNT = 20
        const val NEXT_RUN_DELAY_SECONDS = 2

        fun clampRepeatCount(count: Int): Int = count.coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT)
    }

    private val _state = MutableStateFlow<CustomUssdRepeatState?>(null)
    val state: StateFlow<CustomUssdRepeatState?> = _state.asStateFlow()

    fun startBatch(flow: SimpleCustomUssdFlow, repeatCount: Int): CustomUssdRepeatState {
        val initial = CustomUssdRepeatState(
            flow = flow,
            totalRuns = clampRepeatCount(repeatCount),
            currentRun = 1
        )
        _state.value = initial
        return initial
    }

    fun markRunAwaitingClose(): CustomUssdRepeatState? {
        return _state.updateAndGet { current ->
            if (current == null || !current.hasMoreRuns) {
                null
            } else {
                current.copy(
                    awaitingDialogClose = true,
                    pausedOnError = false,
                    countdownSeconds = null
                )
            }
        }
    }

    fun markPausedOnError(): CustomUssdRepeatState? {
        return _state.updateAndGet { current ->
            if (current == null || !current.hasMoreRuns) {
                null
            } else {
                current.copy(
                    pausedOnError = true,
                    awaitingDialogClose = false,
                    countdownSeconds = null
                )
            }
        }
    }

    fun setCountdown(seconds: Int?): CustomUssdRepeatState? {
        return _state.updateAndGet { current ->
            if (current == null || !current.awaitingDialogClose || current.pausedOnError || !current.hasMoreRuns) {
                current
            } else {
                current.copy(countdownSeconds = seconds)
            }
        }
    }

    fun cancelCountdown(): CustomUssdRepeatState? {
        return _state.updateAndGet { current ->
            current?.copy(countdownSeconds = null)
        }
    }

    fun advanceToNextRun(): CustomUssdRepeatState? {
        return _state.updateAndGet { current ->
            if (current == null || !current.hasMoreRuns) {
                null
            } else {
                current.copy(
                    currentRun = current.currentRun + 1,
                    awaitingDialogClose = false,
                    countdownSeconds = null,
                    pausedOnError = false
                )
            }
        }
    }

    fun clear() {
        _state.value = null
    }
}
