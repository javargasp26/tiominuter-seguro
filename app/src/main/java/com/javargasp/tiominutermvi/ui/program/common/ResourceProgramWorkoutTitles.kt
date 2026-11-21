package com.javargasp.tiominutermvi.ui.program.common

import android.content.Context
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.repository.ProgramWorkoutTitles

/** [ProgramWorkoutTitles] from the same string resources as [workoutTitle]. */
class ResourceProgramWorkoutTitles(private val context: Context) : ProgramWorkoutTitles {
    override fun title(workout: ProgramWorkout): String = context.getString(workoutTitleRes(workout))
}
