package com.javargasp.tiominutermvi.ui.logactivity

import androidx.annotation.StringRes
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity

/*
 * Display texts and icons of logged activities, resolved from the stored codes. Shared by
 * the log-activity screen and the training history.
 */

@StringRes
fun activityTypeLabel(type: ActivityType): Int = when (type) {
    ActivityType.OWN_GYM_ROUTINE -> R.string.activity_type_own_gym_routine
    ActivityType.RUNNING -> R.string.activity_type_running
    ActivityType.WALKING -> R.string.activity_type_walking
    ActivityType.CYCLING -> R.string.activity_type_cycling
    ActivityType.SWIMMING -> R.string.activity_type_swimming
    ActivityType.YOGA -> R.string.activity_type_yoga
    ActivityType.PILATES -> R.string.activity_type_pilates
    ActivityType.DANCE -> R.string.activity_type_dance
    ActivityType.TEAM_SPORT -> R.string.activity_type_team_sport
    ActivityType.RACKET_SPORT -> R.string.activity_type_racket_sport
    ActivityType.MARTIAL_ARTS -> R.string.activity_type_martial_arts
    ActivityType.HIIT -> R.string.activity_type_hiit
    ActivityType.STRETCHING -> R.string.activity_type_stretching
    ActivityType.OTHER -> R.string.activity_type_other
}

/**
 * Decorative icon of an activity type. An emoji, because the app only ships the core
 * Material icon set, which has no sport icons; always shown next to the text label and
 * hidden from TalkBack.
 */
fun activityTypeIcon(type: ActivityType): String = when (type) {
    ActivityType.OWN_GYM_ROUTINE -> "🏋️" // weight lifter
    ActivityType.RUNNING -> "🏃" // runner
    ActivityType.WALKING -> "🚶" // pedestrian
    ActivityType.CYCLING -> "🚴" // cyclist
    ActivityType.SWIMMING -> "🏊" // swimmer
    ActivityType.YOGA -> "🧘" // person in lotus position
    ActivityType.PILATES -> "🤸" // cartwheel
    ActivityType.DANCE -> "💃" // dancer
    ActivityType.TEAM_SPORT -> "🏀" // basketball
    ActivityType.RACKET_SPORT -> "🎾" // tennis
    ActivityType.MARTIAL_ARTS -> "🥋" // martial arts uniform
    ActivityType.HIIT -> "🔥" // fire
    ActivityType.STRETCHING -> "🙆" // arms raised
    ActivityType.OTHER -> "⭐" // star
}

@StringRes
fun intensityLabel(intensity: Intensity): Int = when (intensity) {
    Intensity.EASY -> R.string.intensity_easy
    Intensity.MODERATE -> R.string.intensity_moderate
    Intensity.HARD -> R.string.intensity_hard
}

@StringRes
fun intensityDescription(intensity: Intensity): Int = when (intensity) {
    Intensity.EASY -> R.string.intensity_easy_description
    Intensity.MODERATE -> R.string.intensity_moderate_description
    Intensity.HARD -> R.string.intensity_hard_description
}
