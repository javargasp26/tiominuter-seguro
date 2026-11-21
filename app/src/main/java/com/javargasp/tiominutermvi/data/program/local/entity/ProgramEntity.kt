package com.javargasp.tiominutermvi.data.program.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * A created program: the profile it was generated from. Enum values are stored by name;
 * [focus] by its ProgramFocus code. Programs created before version 2 read 'BALANCED'.
 * [uuid] (lowercase, random) is the program's id in Firestore; [id] stays device-local. Programs
 * created before version 4 got one in the v4 migration.
 */
@Entity(tableName = "programs", indices = [Index("uuid")])
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goal: String,
    val level: String,
    val location: String,
    val weeks: Int,
    val daysPerWeek: Int,
    val age: Int,
    val weightKg: Double,
    val heightCm: Int,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "BALANCED") val focus: String = "BALANCED",
    @ColumnInfo(defaultValue = "") val uuid: String = ""
)
