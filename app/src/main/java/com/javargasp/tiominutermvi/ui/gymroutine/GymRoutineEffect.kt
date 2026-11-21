package com.javargasp.tiominutermvi.ui.gymroutine

import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.ui.base.MviEffect

/**
 * Effects for the Gym Routine screen
 */
sealed class GymRoutineEffect : MviEffect {
    data class NavigateToExercise(val routineId: Int, val position: Int) : GymRoutineEffect()
    object NavigateBack : GymRoutineEffect()
    data class ShowPhotoError(val error: TrainingPhotoError) : GymRoutineEffect()

    /** An unfinished workout was closed for inactivity and saved with this duration. */
    data class ShowSessionSaved(val durationSeconds: Int) : GymRoutineEffect()
}
