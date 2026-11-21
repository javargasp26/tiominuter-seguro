package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The daily routines' progress (see `domain/daily/DailyProgress`): one row, [id] =
 * [SINGLE_ROW_ID]. Every day up to [lastCompletedDay] was finished in [cycle]. [updatedAt] is the
 * local write time (wall-clock millis), or the server time of a pulled version.
 */
@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val lastCompletedDay: Int,
    val cycle: Int,
    val updatedAt: Long
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
