package com.javargasp.tiominutermvi.ui.program.common

import com.javargasp.tiominutermvi.domain.time.MonotonicTimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A single countdown job for the Program ViewModels. The remaining time is computed
 * from an end timestamp of [timeSource] on every tick, so it stays correct after time
 * in the background; the next tick is scheduled for when the displayed second changes.
 * Starting a new countdown cancels the previous one.
 */
class ProgramCountdown(
    private val scope: CoroutineScope,
    private val timeSource: MonotonicTimeSource
) {
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Calls [onTick] with the remaining whole seconds (rounded up), starting with
     * [durationSeconds], then [onFinished] once at zero.
     */
    fun start(durationSeconds: Int, onTick: (Int) -> Unit, onFinished: suspend () -> Unit) {
        cancel()
        onTick(durationSeconds)
        val endAtMillis = timeSource.nowMillis() + durationSeconds * MILLIS_PER_SECOND
        job = scope.launch {
            while (true) {
                val remainingMillis = endAtMillis - timeSource.nowMillis()
                if (remainingMillis <= 0) break
                val remainingSeconds = ((remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
                onTick(remainingSeconds)
                delay(remainingMillis - (remainingSeconds - 1) * MILLIS_PER_SECOND)
            }
            // Detach first: onFinished usually starts the next countdown.
            job = null
            onFinished()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val GET_READY_SECONDS = 5
    }
}
