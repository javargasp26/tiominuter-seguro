package com.javargasp.tiominutermvi.data.sync

import com.javargasp.tiominutermvi.data.local.entity.DailyProgressEntity
import com.javargasp.tiominutermvi.data.local.entity.TrainingLogEntity
import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.data.local.entity.WeeklyGoalEntity

/**
 * `users/{uid}/trainingLog/{entryId}` ↔ `training_log`, exactly as docs/firestore-model.md maps
 * them: the device id, `photoPath` and the local `updatedAt` stay local; `refId` is converted by
 * the caller (PROGRAM refIds name the program's uuid in Firestore).
 */
object TrainingLogDocMapper {
    fun toFields(entry: TrainingLogEntity, remoteRefId: String?): Map<String, Any?> = mapOf(
        "entryId" to entry.uuid,
        "dateLocal" to entry.dateLocal,
        "completedAt" to entry.completedAt,
        "source" to entry.source,
        "refId" to remoteRefId,
        "title" to entry.title,
        "durationSeconds" to entry.durationSeconds,
        "activityType" to entry.activityType,
        "intensity" to entry.intensity,
        "note" to entry.note,
        "createdAt" to entry.createdAt
    )

    /** The row of a pulled document, keeping the local id and photo; null when it can't be read. */
    fun toEntity(
        uuid: String,
        fields: Map<String, Any?>,
        existing: TrainingLogEntity?,
        localRefId: String?,
        updatedAtMillis: Long
    ): TrainingLogEntity? {
        val dateLocal = fields.string("dateLocal") ?: return null
        val completedAt = fields.long("completedAt") ?: return null
        val source = fields.string("source") ?: return null
        val title = fields.string("title") ?: return null
        val createdAt = fields.long("createdAt") ?: return null
        return TrainingLogEntity(
            id = existing?.id ?: 0,
            uuid = uuid,
            dateLocal = dateLocal,
            completedAt = completedAt,
            source = source,
            refId = localRefId,
            title = title,
            durationSeconds = fields.int("durationSeconds"),
            activityType = fields.string("activityType"),
            intensity = fields.string("intensity"),
            note = fields.string("note"),
            photoPath = existing?.photoPath,
            createdAt = createdAt,
            updatedAt = updatedAtMillis
        )
    }
}

/** `users/{uid}/weeklyGoals/{effectiveFromWeek}` ↔ `weekly_goal`. */
object WeeklyGoalDocMapper {
    fun toFields(goal: WeeklyGoalEntity): Map<String, Any?> = mapOf(
        "effectiveFromWeek" to goal.effectiveFromWeek,
        "workoutsPerWeek" to goal.workoutsPerWeek
    )

    fun toEntity(week: String, fields: Map<String, Any?>, updatedAtMillis: Long): WeeklyGoalEntity? {
        val workouts = fields.int("workoutsPerWeek") ?: return null
        return WeeklyGoalEntity(effectiveFromWeek = week, workoutsPerWeek = workouts, updatedAt = updatedAtMillis)
    }
}

/** `users/{uid}/dailyProgress/state` ↔ `daily_progress` (one row, no id field). */
object DailyProgressDocMapper {
    fun toFields(progress: DailyProgressEntity): Map<String, Any?> = mapOf(
        "lastCompletedDay" to progress.lastCompletedDay,
        "cycle" to progress.cycle
    )

    /** Null when a field is missing or out of the model's range (the document is then ignored). */
    fun toDomain(fields: Map<String, Any?>): DailyProgress? {
        val day = fields.long("lastCompletedDay")?.takeIf { it in 0..999 } ?: return null
        val cycle = fields.long("cycle")?.takeIf { it in 0..Int.MAX_VALUE.toLong() } ?: return null
        return DailyProgress(lastCompletedDay = day.toInt(), cycle = cycle.toInt())
    }
}
