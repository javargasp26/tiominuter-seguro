package com.javargasp.tiominutermvi.domain.program.model

/** A stored program with its overall progress (completed / total workouts). */
data class ProgramSummary(
    val id: Long,
    val profile: ProgramProfile,
    val createdAt: Long,
    val completedWorkouts: Int,
    val totalWorkouts: Int
)

/** A stored program with every week, workout and completion state. */
data class ProgramDetail(
    val summary: ProgramSummary,
    val weeks: List<ProgramWeekDetail>
)

data class ProgramWeekDetail(
    val weekNumber: Int,
    val isDeload: Boolean,
    val workouts: List<ProgramWorkoutDetail>
) {
    val completedWorkouts: Int get() = workouts.count { it.isCompleted }
}

/**
 * One stored workout. [week] starts at 1, [dayIndex] at 0. [completedPositions]
 * are the strength slots done in the current attempt.
 */
data class ProgramWorkoutDetail(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val workout: ProgramWorkout,
    val isCompleted: Boolean,
    val completedPositions: Set<Int>
)
