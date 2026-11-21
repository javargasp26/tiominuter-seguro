package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Completion marker for one exercise slot of a gym routine.
 *
 * The foreign key points to the routine only (not to the exercise slot), so
 * progress survives when a routine's exercises are refreshed, but is removed
 * when the routine itself is deleted.
 */
@Entity(
    tableName = "gym_exercise_progress",
    primaryKeys = ["routineId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = GymRoutineEntity::class,
            parentColumns = ["routineId"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class GymExerciseProgressEntity(
    val routineId: Int,
    val position: Int,
    val completedAt: Long
)
