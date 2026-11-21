package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * The open gym session of a routine (see `domain/gymsession`): [sessionStartedAt] is the first
 * completed set and [lastSetAt] the latest one (wall-clock millis). At most one per routine; the
 * row is removed when the session is recorded or discarded, and with its routine (CASCADE, like
 * the progress).
 */
@Entity(
    tableName = "gym_session",
    foreignKeys = [
        ForeignKey(
            entity = GymRoutineEntity::class,
            parentColumns = ["routineId"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GymSessionEntity(
    @PrimaryKey val routineId: Int,
    val sessionStartedAt: Long,
    val lastSetAt: Long
)
