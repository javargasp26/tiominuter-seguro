package com.javargasp.tiominutermvi.data.program.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/** Completion marker for one exercise ([position]) of a strength workout. */
@Entity(
    tableName = "program_slot_progress",
    primaryKeys = ["programId", "week", "dayIndex", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ProgramWorkoutEntity::class,
            parentColumns = ["programId", "week", "dayIndex"],
            childColumns = ["programId", "week", "dayIndex"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProgramSlotProgressEntity(
    val programId: Long,
    val week: Int,
    val dayIndex: Int,
    val position: Int,
    val completedAt: Long
)
