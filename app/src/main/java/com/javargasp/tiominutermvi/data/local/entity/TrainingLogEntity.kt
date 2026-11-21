package com.javargasp.tiominutermvi.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry of the unified training history (see `domain/traininglog`). [source] is
 * the raw `TrainingSource` name; [dateLocal] is "yyyy-MM-dd" and indexed for date
 * ranges; [uuid] is unique (future sync id). No foreign keys: an entry outlives the
 * routine or program it came from.
 */
@Entity(
    tableName = "training_log",
    indices = [
        Index("dateLocal"),
        Index(value = ["uuid"], unique = true)
    ]
)
data class TrainingLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val dateLocal: String,
    val completedAt: Long,
    val source: String,
    val refId: String?,
    val title: String,
    val durationSeconds: Int?,
    val activityType: String?,
    val intensity: String?,
    val note: String?,
    val photoPath: String?,
    val createdAt: Long,
    val updatedAt: Long
)
