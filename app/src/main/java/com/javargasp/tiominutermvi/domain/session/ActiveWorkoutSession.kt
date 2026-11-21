package com.javargasp.tiominutermvi.domain.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The active session of the workout a ViewModel shows (daily, program cardio): its
 * [ActiveTimeMeter] plus the persisted session in [tracker]. The ViewModel says when its timer
 * runs ([setTimerRunning], counted in the background too), when an untimed step is shown
 * ([setActiveOnScreen], counted only while visible), when the screen is visible ([setVisible])
 * and when the person acts ([onUserAction]); the active time is saved every [CHECKPOINT_MILLIS]
 * while counting and on every change, with a single cheap write.
 *
 * The session is stored from [start] (the first real action, so opening and leaving a workout
 * records nothing). [finish] ends it and returns the active seconds to record, once.
 */
class ActiveWorkoutSession(
    private val scope: CoroutineScope,
    private val tracker: ActiveSessionTracker,
    private val meter: ActiveTimeMeter,
    private val key: String
) {
    private var started = false
    private var finished = false
    private var ticker: Job? = null

    val isStarted: Boolean get() = started

    val activeSeconds: Int get() = meter.seconds

    /**
     * Stores the session (an open one of this kind is recorded first) with the active time so
     * far. Returns the sessions closed meanwhile, to announce. Only the first call does anything.
     */
    suspend fun start(): List<ClosedSession> {
        if (started || finished) return emptyList()
        started = true
        tracker.start(key)
        checkpoint()
        refreshTicker()
        return tracker.takeClosedSessions()
    }

    fun setTimerRunning(running: Boolean) {
        meter.setTimerRunning(running)
        changed()
    }

    fun setActiveOnScreen(active: Boolean) {
        meter.setActiveOnScreen(active)
        changed()
    }

    fun setVisible(visible: Boolean) {
        meter.setVisible(visible)
        changed()
    }

    /** Any action in the workout counts as activity (it keeps the session open). */
    fun onUserAction() {
        changed()
    }

    /** The workout finished normally: the session ends; returns the active seconds to record. */
    suspend fun finish(): Int {
        meter.setTimerRunning(false)
        meter.setActiveOnScreen(false)
        finished = true
        stopTicker()
        if (started) tracker.finish(key)
        return meter.seconds
    }

    /** The ViewModel is cleared: stop saving (the last change already saved the active time). */
    fun close() {
        stopTicker()
    }

    private fun changed() {
        refreshTicker()
        if (started && !finished) scope.launch { checkpoint() }
    }

    private suspend fun checkpoint() {
        if (!started || finished) return
        // The old session was closed (inactive): this one starts from now
        if (!tracker.update(key, meter.seconds)) meter.reset()
    }

    /**
     * Saves every [CHECKPOINT_MILLIS] while counting. It stops when the active time didn't move
     * since the last save (nothing to write; a frozen clock) and restarts on the next change.
     */
    private fun refreshTicker() {
        if (started && !finished && meter.isCounting) {
            if (ticker?.isActive != true) {
                ticker = scope.launch {
                    var saved = -1
                    while (meter.seconds != saved) {
                        saved = meter.seconds
                        delay(CHECKPOINT_MILLIS)
                        checkpoint()
                    }
                }
            }
        } else {
            stopTicker()
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        /** How often the active time is saved while it counts. */
        const val CHECKPOINT_MILLIS = 5_000L
    }
}
