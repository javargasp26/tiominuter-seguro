package com.javargasp.tiominutermvi.domain.program.model

import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode

/**
 * The locale-free name of this workout (the core's [ProgramTitleCode]): what the history and
 * the group feed resolve into the viewer's language, and what `workoutTitleRes` shows.
 */
fun ProgramWorkout.titleCode(): ProgramTitleCode = when (this) {
    is ProgramWorkout.Strength -> when (title) {
        StrengthDayTitle.FULL_BODY_A -> ProgramTitleCode.FULL_BODY_A
        StrengthDayTitle.FULL_BODY_B -> ProgramTitleCode.FULL_BODY_B
        StrengthDayTitle.FULL_BODY_C -> ProgramTitleCode.FULL_BODY_C
        StrengthDayTitle.UPPER_A -> ProgramTitleCode.UPPER_A
        StrengthDayTitle.UPPER_B -> ProgramTitleCode.UPPER_B
        StrengthDayTitle.LOWER_A -> ProgramTitleCode.LOWER_A
        StrengthDayTitle.LOWER_B -> ProgramTitleCode.LOWER_B
        StrengthDayTitle.PUSH -> ProgramTitleCode.PUSH
        StrengthDayTitle.PULL -> ProgramTitleCode.PULL
        StrengthDayTitle.LEGS -> ProgramTitleCode.LEGS
        StrengthDayTitle.FULL_BODY -> ProgramTitleCode.FULL_BODY
        StrengthDayTitle.UPPER -> ProgramTitleCode.UPPER
        StrengthDayTitle.LOWER -> ProgramTitleCode.LOWER
        StrengthDayTitle.UPPER_C -> ProgramTitleCode.UPPER_C
        StrengthDayTitle.LOWER_C -> ProgramTitleCode.LOWER_C
    }
    is ProgramWorkout.OutdoorRun -> if (walk) ProgramTitleCode.BRISK_WALK else ProgramTitleCode.OUTDOOR_RUN
    is ProgramWorkout.Treadmill -> ProgramTitleCode.TREADMILL
    is ProgramWorkout.HomeCircuit -> ProgramTitleCode.HOME_CIRCUIT
}
