package com.javargasp.tiominutermvi.data.traininglog

import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource

/** Entity → domain; null (and logged) when the stored source is unknown. */
fun TrainingLogEntity.toDomainOrNull(): TrainingLogEntry? {
    val trainingSource = TrainingSource.entries.firstOrNull { it.name == source }
    if (trainingSource == null) {
        println("TrainingLog: skipping entry $id with unknown source '$source'")
        return null
    }
    return TrainingLogEntry(
        id = id,
        uuid = uuid,
        dateLocal = dateLocal,
        completedAt = completedAt,
        source = trainingSource,
        refId = refId,
        title = title,
        durationSeconds = durationSeconds,
        activityType = activityType,
        intensity = intensity,
        note = note,
        photoPath = photoPath,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/** Domain → entity; the source is stored by name. */
fun TrainingLogEntry.toEntity(): TrainingLogEntity = TrainingLogEntity(
    id = id,
    uuid = uuid,
    dateLocal = dateLocal,
    completedAt = completedAt,
    source = source.name,
    refId = refId,
    title = title,
    durationSeconds = durationSeconds,
    activityType = activityType,
    intensity = intensity,
    note = note,
    photoPath = photoPath,
    createdAt = createdAt,
    updatedAt = updatedAt
)
