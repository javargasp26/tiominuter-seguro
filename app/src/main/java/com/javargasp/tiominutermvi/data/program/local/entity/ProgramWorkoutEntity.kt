package com.javargasp.tiominutermvi.data.program.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * One generated workout. [payload] is the domain workout serialized as JSON, so a
 * stored program never changes when the templates change. [kind] mirrors the payload
 * type (STRENGTH, OUTDOOR_RUN, TREADMILL, HOME_CIRCUIT) for queries.
 */
@Entity(
    tableName = "program_workouts",
    primaryKeys = ["programId", "week", "dayIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProgramWorkoutEntity(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val kind: String,
    val isDeload: Boolean,
    val payload: String
)
