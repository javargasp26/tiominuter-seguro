package com.javargasp.tiominutermvi.domain.traininglog

/**
 * One completed workout in the unified training history.
 *
 * [uuid] is created with the entry and will identify it in the future sync.
 * [dateLocal] is the local calendar date ("yyyy-MM-dd") of [completedAt] in the
 * device time zone at that moment; [completedAt], [createdAt] and [updatedAt] are
 * epoch milliseconds. [refId] points to what was trained (e.g. "gym:64").
 * [activityType] and [intensity] are the codes of [ActivityType] and [Intensity] for
 * [TrainingSource.ACTIVITY] entries (null otherwise); [note] is the optional note of a
 * logged activity; [photoPath] is not used yet. For an ACTIVITY entry [title] holds the
 * custom name of [ActivityType.OTHER] and is empty for the other types (their label comes
 * from the code at display time).
 */
data class TrainingLogEntry(
    val id: Long,
    val uuid: String,
    val dateLocal: String,
    val completedAt: Long,
    val source: TrainingSource,
    val refId: String?,
    val title: String,
    val durationSeconds: Int?,
    val activityType: String? = null,
    val intensity: String? = null,
    val note: String? = null,
    val photoPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** The activity type of an ACTIVITY entry; null for other sources or an unknown code. */
    val activity: ActivityType? get() = activityType?.let(ActivityType::fromCode)

    /** The intensity of an ACTIVITY entry; null for other sources or an unknown code. */
    val intensityLevel: Intensity? get() = intensity?.let(Intensity::fromCode)

    val isActivity: Boolean get() = source == TrainingSource.ACTIVITY
}

/**
 * Where a training entry comes from: recorded automatically by a routine or program, or
 * [ACTIVITY], logged by hand (any workout done outside the app's routines).
 */
enum class TrainingSource { DAILY, GYM, PROGRAM, ACTIVITY }
