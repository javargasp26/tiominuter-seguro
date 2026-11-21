package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local copy of an exercise (name, optional Spanish name, animation asset).
 * [nameEs] was added in database version 6; older rows have null until the next refresh.
 */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey
    val exerciseId: Int,
    val name: String,
    val animation: String,
    val nameEs: String? = null
)
