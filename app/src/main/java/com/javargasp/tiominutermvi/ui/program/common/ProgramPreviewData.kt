package com.javargasp.tiominutermvi.ui.program.common

import com.javargasp.tiominutermvi.domain.program.model.Equipment
import com.javargasp.tiominutermvi.domain.program.model.Impact
import com.javargasp.tiominutermvi.domain.program.model.Mechanic
import com.javargasp.tiominutermvi.domain.program.model.MuscleGroup
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegment
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegmentLabel

/** Sample data for the Program screens' previews. */
internal object ProgramPreviewData {

    private fun exercise(id: Int, name: String, nameEs: String, animation: String, perSide: Boolean = false) = ProgramExercise(
        exerciseId = id,
        name = name,
        nameEs = nameEs,
        animation = animation,
        primaryMuscles = listOf(MuscleGroup.FULL_BODY),
        equipment = Equipment.BODYWEIGHT,
        mechanic = Mechanic.COMPOUND,
        impact = Impact.LOW,
        locations = setOf(ProgramLocation.HOME, ProgramLocation.GYM),
        perSide = perSide
    )

    val exercises: Map<Int, ProgramExercise> = listOf(
        exercise(2, "Squats", "Sentadillas", "2_squat.gif"),
        exercise(3, "Push-ups", "Flexiones", "3_push_up.gif"),
        exercise(4, "Plank", "Plancha", "4_plank.gif"),
        exercise(70, "Dumbbell Row", "Remo con mancuerna", "70_dumbbell_row.gif", perSide = true),
        exercise(1, "Jumping Jacks", "Saltos de tijera", "1_jumping_jack.gif"),
        exercise(112, "Bulgarian Split Squat", "Sentadilla búlgara", "112_bulgarian_split_squat.gif", perSide = true),
        exercise(120, "Side Plank", "Plancha lateral", "120_side_plank.gif", perSide = true)
    ).associateBy { it.exerciseId }

    val strength = ProgramWorkout.Strength(
        title = StrengthDayTitle.FULL_BODY_A,
        slots = listOf(
            StrengthSlot(0, 2, ProgramSlotType.REPS, listOf(10, 10, 10, 10), 90),
            StrengthSlot(1, 3, ProgramSlotType.REPS, listOf(8, 8, 8), 90),
            StrengthSlot(2, 70, ProgramSlotType.REPS, listOf(10, 10, 10), 90, perSide = true),
            StrengthSlot(3, 4, ProgramSlotType.TIME, listOf(30, 30, 30), 60),
            StrengthSlot(4, 120, ProgramSlotType.TIME, listOf(30, 30, 30), 60, perSide = true)
        )
    )

    val treadmill = ProgramWorkout.Treadmill(
        listOf(
            TreadmillSegment(300, 5.0, 1.0, TreadmillSegmentLabel.WARM_UP),
            TreadmillSegment(60, 7.0, 1.0, TreadmillSegmentLabel.JOG),
            TreadmillSegment(120, 5.0, 1.0, TreadmillSegmentLabel.WALK),
            TreadmillSegment(60, 7.0, 2.0, TreadmillSegmentLabel.JOG),
            TreadmillSegment(300, 5.0, 0.0, TreadmillSegmentLabel.COOL_DOWN)
        )
    )

    val circuit = ProgramWorkout.HomeCircuit(
        rounds = 3,
        workSeconds = 30,
        restSeconds = 30,
        restBetweenRoundsSeconds = 60,
        exerciseIds = listOf(1, 2, 3, 4)
    )
}
