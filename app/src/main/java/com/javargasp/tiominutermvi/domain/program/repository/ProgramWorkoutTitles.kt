package com.javargasp.tiominutermvi.domain.program.repository

import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout

/**
 * The display title of a workout ("Full body A", "Treadmill"...) in the app's current
 * language, for the training history entry recorded when the workout is completed.
 */
fun interface ProgramWorkoutTitles {
    fun title(workout: ProgramWorkout): String
}
