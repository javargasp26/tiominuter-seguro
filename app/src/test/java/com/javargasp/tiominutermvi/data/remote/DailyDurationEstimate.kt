package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.RoutineExercise

/**
 * The single estimate of a daily routine's **conservative minimum active time**, as
 * `WorkoutViewModel` measures it (see "Workout sessions" in CLAUDE.md): a running timer counts
 * (the 5-s Get Ready, a TIME exercise, a rest) and a REPS exercise counts while it is on screen.
 *
 * - TIME (not rest): `amount` + [GET_READY_SECONDS] (the Get Ready countdown counts; waiting
 *   before tapping it doesn't, so no transition time is added).
 * - Rest (TIME, [RoutineExercise.isRest]): `amount` (it starts on load, no Get Ready).
 * - REPS: `amount` × [SECONDS_PER_REP] (a fast pace; reading the screen is not added).
 *
 * Skipping exercises is not modelled: the minimum is for a person who does every slot.
 */
object DailyDurationEstimate {

    /** The daily workout's Get Ready countdown (`WorkoutViewModel.startReadyCountdown`). */
    const val GET_READY_SECONDS = 5

    /** A fast rep, so the estimate stays a floor. */
    const val SECONDS_PER_REP = 2

    fun conservativeMinimumSeconds(slots: List<RoutineExercise>): Int = slots.sumOf { slot ->
        when {
            slot.type == ExerciseType.REPS -> slot.amount * SECONDS_PER_REP
            slot.isRest -> slot.amount
            else -> slot.amount + GET_READY_SECONDS
        }
    }
}
