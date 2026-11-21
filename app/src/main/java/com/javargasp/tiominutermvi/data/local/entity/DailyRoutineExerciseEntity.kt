package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One exercise slot inside a daily routine. [position] is the exercise's index
 * inside the routine, so the same exercise (e.g. rest) can appear more than once.
 *
 * [type] is the raw "REPS"/"TIME" string; [amount] is seconds for TIME and
 * target reps for REPS. [exerciseId] points to the shared `exercises` table
 * without a foreign key.
 */
@Entity(
    tableName = "daily_routine_exercises",
    primaryKeys = ["routineId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DailyRoutineEntity::class,
            parentColumns = ["routineId"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("exerciseId"),
        Index("routineId")
    ]
)
data class DailyRoutineExerciseEntity(
    val routineId: Int,
    val position: Int,
    val exerciseId: Int,
    val type: String,
    val amount: Int
)
