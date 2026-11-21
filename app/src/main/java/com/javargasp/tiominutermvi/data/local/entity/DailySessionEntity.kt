package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The open daily workout session (see `domain/session/ActiveSession`): at most one row. Its
 * routine, its start and last activity (wall-clock millis) and the active seconds saved so far.
 * No foreign key: like a training entry, it outlives a catalog refresh.
 */
@Entity(tableName = "daily_session")
data class DailySessionEntity(
    @PrimaryKey val routineId: Int,
    val startedAt: Long,
    val activeSeconds: Int,
    val lastActivityAt: Long
)
