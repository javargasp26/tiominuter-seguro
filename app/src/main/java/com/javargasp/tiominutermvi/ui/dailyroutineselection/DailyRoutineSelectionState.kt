package com.javargasp.tiominutermvi.ui.dailyroutineselection

import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.base.MviState

/** How a day shows in the list ([DailyUnlockRules]). */
enum class DailyDayStatus { COMPLETED, AVAILABLE, LOCKED }

/**
 * Represents the UI state of the Daily Routine Selection screen. [progress] is null until its
 * first read (the screen shows loading until both the routines and the progress are known).
 */
data class DailyRoutineSelectionState(
    val isLoading: Boolean = false,
    val routines: List<Routine> = emptyList(),
    val error: String? = null,
    val progress: DailyProgress? = null,
    val showRestartDialog: Boolean = false
) : MviState {

    private val lastRoutineId: Int? get() = routines.maxOfOrNull { it.routineId }

    /** The day Home calls available, highlighted ("Available today"). */
    val nextDay: Int? get() = progress?.let { DailyUnlockRules.nextAvailableDay(it, lastRoutineId) }

    /** Every day was finished in this cycle: "Start again from Day 1" is offered. */
    val isCycleComplete: Boolean get() = progress?.let { DailyUnlockRules.isCycleComplete(it, lastRoutineId) } == true

    /** "Restart days" only once a day was finished (restarting at Day 1 would change nothing). */
    val canRestart: Boolean get() = (progress?.lastCompletedDay ?: 0) > 0

    fun statusOf(routineId: Int): DailyDayStatus {
        val current = progress ?: return DailyDayStatus.LOCKED
        return when {
            DailyUnlockRules.isCompleted(routineId, current) -> DailyDayStatus.COMPLETED
            DailyUnlockRules.isUnlocked(routineId, current) -> DailyDayStatus.AVAILABLE
            else -> DailyDayStatus.LOCKED
        }
    }

    /** The name of [day] (its routine's title when the content has one). */
    fun dayName(day: Int): WorkoutName.Daily =
        routines.firstOrNull { it.routineId == day }?.let(WorkoutNameResolver::dailyName) ?: WorkoutName.Daily(day)
}
