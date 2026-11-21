package com.javargasp.tiominutermvi.domain.traininglog

/**
 * Kind of a logged activity, stored in `training_log.activityType` by its [code]. Labels
 * are resolved at display time from string resources, never stored.
 */
enum class ActivityType(val code: String) {
    OWN_GYM_ROUTINE("OWN_GYM_ROUTINE"),
    RUNNING("RUNNING"),
    WALKING("WALKING"),
    CYCLING("CYCLING"),
    SWIMMING("SWIMMING"),
    YOGA("YOGA"),
    PILATES("PILATES"),
    DANCE("DANCE"),
    TEAM_SPORT("TEAM_SPORT"),
    RACKET_SPORT("RACKET_SPORT"),
    MARTIAL_ARTS("MARTIAL_ARTS"),
    HIIT("HIIT"),
    STRETCHING("STRETCHING"),

    /** Needs a custom name, stored in the entry's title. */
    OTHER("OTHER");

    companion object {
        fun fromCode(code: String): ActivityType? = entries.firstOrNull { it.code == code }
    }
}

/** How hard a logged activity was, stored in `training_log.intensity` by its [code]. */
enum class Intensity(val code: String) {
    EASY("EASY"),
    MODERATE("MODERATE"),
    HARD("HARD");

    companion object {
        fun fromCode(code: String): Intensity? = entries.firstOrNull { it.code == code }
    }
}
