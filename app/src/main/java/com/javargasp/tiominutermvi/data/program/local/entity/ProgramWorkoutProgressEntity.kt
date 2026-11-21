package com.javargasp.tiominutermvi.data.program.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** Completion marker for a whole workout; kept when the workout is done again. */
@Entity(
    tableName = "program_workout_progress",
    primaryKeys = ["programId", "week", "dayIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramWorkoutEntity::class,
            parentColumns = ["programId", "week", "dayIndex"],
            childColumns = ["programId", "week", "dayIndex"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProgramWorkoutProgressEntity(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val completedAt: Long
)
