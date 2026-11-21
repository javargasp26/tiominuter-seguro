package com.javargasp.tiominutermvi.domain.program.model

import kotlinx.serialization.Serializable

/** What the program is for. */
@Serializable
enum class ProgramGoal { GAIN_MUSCLE, LOSE_FAT }

/** Training experience of the user. */
@Serializable
enum class ProgramLevel { BEGINNER, INTERMEDIATE, ADVANCED }

/**
 * Where a program puts the emphasis; picks the split (see `program_templates.json`).
 * Stored in `programs.focus` by its [code]. BALANCED is the split every program had
 * before the focus existed.
 */
enum class ProgramFocus(val code: String) {
    BALANCED("BALANCED"),
    LOWER_BODY("LOWER_BODY"),
    UPPER_BODY("UPPER_BODY"),
    CORE("CORE");

    companion object {
        val DEFAULT = BALANCED

        fun fromCode(code: String): ProgramFocus? = entries.firstOrNull { it.code == code }
    }
}

/** Where the user trains; filters the equipment an exercise may need. */
@Serializable
enum class ProgramLocation { HOME, GYM }

/** Main equipment an exercise needs. */
@Serializable
enum class Equipment {
    BODYWEIGHT, DUMBBELL, BARBELL, MACHINE, CABLE, PULL_UP_BAR, BENCH, TREADMILL, BIKE, NONE
}

@Serializable
enum class Mechanic { COMPOUND, ISOLATION, CARDIO, CORE }

/** HIGH impact exercises (jumps, running) are excluded from low-impact programs. */
@Serializable
enum class Impact { LOW, HIGH }

@Serializable
enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, FULL_BODY, CARDIO
}

/** How a strength slot is performed: reps per set, or a hold of N seconds per set. */
@Serializable
enum class ProgramSlotType { REPS, TIME }

/** Cardio workout formats. */
@Serializable
enum class CardioType { OUTDOOR_RUN, TREADMILL, HOME_CIRCUIT }

/**
 * Title of a strength day, shown as "Full body A", "Upper A", "Push"... Stored by name in
 * the workout payloads, so values are only ever added. FULL_BODY, UPPER, LOWER, UPPER_C and
 * LOWER_C come from the focused splits.
 */
@Serializable
enum class StrengthDayTitle {
    FULL_BODY_A, FULL_BODY_B, FULL_BODY_C, UPPER_A, UPPER_B, LOWER_A, LOWER_B, PUSH, PULL, LEGS,
    FULL_BODY, UPPER, LOWER, UPPER_C, LOWER_C
}

/** What a treadmill segment asks the user to do. */
@Serializable
enum class TreadmillSegmentLabel { WARM_UP, JOG, WALK, INCLINE_WALK, COOL_DOWN }
