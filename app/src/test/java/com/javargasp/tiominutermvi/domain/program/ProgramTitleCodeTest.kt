package com.javargasp.tiominutermvi.domain.program

import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.titleCode
import com.javargasp.tiominutermvi.domain.traininglog.ProgramTitleCode
import com.javargasp.tiominutermvi.testutil.StringResources
import com.javargasp.tiominutermvi.ui.program.common.workoutTitleRes
import org.junit.Assert.assertEquals
import org.junit.Test

/** The program workout → its locale-free code (history, feed titleKey) → the same title as before. */
class ProgramTitleCodeTest {

    private val cardio = listOf(
        ProgramWorkout.OutdoorRun(distanceKm = 3.0, walk = false) to ProgramTitleCode.OUTDOOR_RUN,
        ProgramWorkout.OutdoorRun(distanceKm = 3.0, walk = true) to ProgramTitleCode.BRISK_WALK,
        ProgramWorkout.Treadmill(segments = emptyList()) to ProgramTitleCode.TREADMILL,
        ProgramWorkout.HomeCircuit(3, 40, 20, 60, emptyList()) to ProgramTitleCode.HOME_CIRCUIT
    )

    @Test
    fun `each strength title is the code of the same name`() {
        StrengthDayTitle.entries.forEach { title ->
            assertEquals(title.name, ProgramWorkout.Strength(title, emptyList()).titleCode().name)
        }
    }

    @Test
    fun `each cardio kind has its code`() {
        cardio.forEach { (workout, code) -> assertEquals(code, workout.titleCode()) }
    }

    @Test
    fun `the program screens show the same titles as before (program_title_ + the code)`() {
        val workouts = StrengthDayTitle.entries.map { ProgramWorkout.Strength(it, emptyList()) } + cardio.map { it.first }
        workouts.forEach { workout ->
            assertEquals(
                "program_title_${workout.titleCode().name.lowercase()}",
                StringResources.nameOf(workoutTitleRes(workout))
            )
        }
        assertEquals(ProgramTitleCode.entries.toSet(), workouts.map { it.titleCode() }.toSet())
    }
}
