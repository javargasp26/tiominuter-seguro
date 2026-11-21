package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A daily routine. Deleting a row cascades to its exercise slots.
 */
@Entity(tableName = "daily_routines")
data class DailyRoutineEntity(
    @PrimaryKey
    val routineId: Int,
    val routineDay: String,
    val title: String?
)
