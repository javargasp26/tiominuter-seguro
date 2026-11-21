package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A gym routine. Deleting a row cascades to its exercises and progress entries.
 * [routineNameEs] was added in database version 6; older rows have null until the next refresh.
 */
@Entity(tableName = "gym_routines")
data class GymRoutineEntity(
    @PrimaryKey
    val routineId: Int,
    val routineName: String,
    val routineNameEs: String? = null
)
