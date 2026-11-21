package com.javargasp.tiominutermvi.data.program.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * The open session of a strength workout (the gym model, see `domain/session`):
 * [sessionStartedAt] is its first completed set, [lastActivityAt] the latest one (wall-clock
 * millis). At most one per workout; it goes with the workout (CASCADE).
 */
@Entity(
    tableName = "program_strength_session",
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
data class ProgramStrengthSessionEntity(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val sessionStartedAt: Long,
    val lastActivityAt: Long
)

/**
 * The open session of a cardio workout (treadmill, circuit, outdoor run), measured in active
 * time: at most one row for all of them. It goes with the workout (CASCADE).
 */
@Entity(
    tableName = "program_cardio_session",
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
data class ProgramCardioSessionEntity(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val startedAt: Long,
    val activeSeconds: Int,
    val lastActivityAt: Long
)
