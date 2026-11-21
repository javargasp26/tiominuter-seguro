package com.javargasp.tiominutermvi.domain.traininglog

/**
 * What the person entered for a logged activity. [durationMinutes] and [intensity] are
 * required; [customName] only for [ActivityType.OTHER]; [note] is optional.
 */
data class ActivityEntryInput(
    val type: ActivityType?,
    val customName: String,
    val durationMinutes: Int?,
    val intensity: Intensity?,
    val completedAt: Long,
    val note: String
)

/** A rule an [ActivityEntryInput] breaks. */
enum class ActivityEntryError {
    TYPE_MISSING,
    NAME_MISSING,
    NAME_TOO_LONG,
    DURATION_OUT_OF_RANGE,
    INTENSITY_MISSING,
    DATE_TOO_EARLY,
    DATE_IN_FUTURE,
    NOTE_TOO_LONG
}

/** Pure validation rules for logged activities. */
object ActivityEntryRules {

    val DURATION_MINUTES = 1..600
    const val NAME_MAX_LENGTH = 40
    const val NOTE_MAX_LENGTH = 200

    /** The oldest day allowed: today and the 6 days before it. */
    const val DAYS_BACK = 6

    /** 00:00 (device zone) of the oldest allowed day. */
    fun windowStart(localDateProvider: LocalDateProvider): Long =
        localDateProvider.startOfDay(localDateProvider.todayPlusDays(-DAYS_BACK))

    /**
     * Every rule [input] breaks; empty when it can be saved. [completedAt] must lie between
     * [earliestMillis] (normally [windowStart]) and [nowMillis], both included.
     */
    fun validate(input: ActivityEntryInput, nowMillis: Long, earliestMillis: Long): Set<ActivityEntryError> {
        val errors = mutableSetOf<ActivityEntryError>()
        if (input.type == null) errors += ActivityEntryError.TYPE_MISSING
        if (input.type == ActivityType.OTHER) {
            val name = input.customName.trim()
            if (name.isEmpty()) errors += ActivityEntryError.NAME_MISSING
            if (name.length > NAME_MAX_LENGTH) errors += ActivityEntryError.NAME_TOO_LONG
        }
        if (input.durationMinutes == null || input.durationMinutes !in DURATION_MINUTES) {
            errors += ActivityEntryError.DURATION_OUT_OF_RANGE
        }
        if (input.intensity == null) errors += ActivityEntryError.INTENSITY_MISSING
        if (input.completedAt > nowMillis) errors += ActivityEntryError.DATE_IN_FUTURE
        if (input.completedAt < earliestMillis) errors += ActivityEntryError.DATE_TOO_EARLY
        if (input.note.length > NOTE_MAX_LENGTH) errors += ActivityEntryError.NOTE_TOO_LONG
        return errors
    }

    /** The stored title: the trimmed custom name for OTHER, empty for every other type. */
    fun titleFor(input: ActivityEntryInput): String =
        if (input.type == ActivityType.OTHER) input.customName.trim() else ""

    /** The stored note: trimmed, null when blank. */
    fun noteFor(input: ActivityEntryInput): String? = input.note.trim().ifEmpty { null }
}

/** Thrown when an activity that breaks [ActivityEntryRules] is saved. */
class InvalidActivityEntryException(val errors: Set<ActivityEntryError>) :
    IllegalArgumentException("Invalid activity entry: $errors")
