package com.javargasp.tiominutermvi.data.program.mapper

import com.javargasp.tiominutermvi.domain.program.model.GeneratedProgram
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeek
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.StrengthDayTitle
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegment
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegmentLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramEntityMappersTest {

    private val workouts = listOf(
        ProgramWorkout.Strength(
            StrengthDayTitle.FULL_BODY_A,
            listOf(
                StrengthSlot(0, 55, ProgramSlotType.REPS, listOf(8, 8, 8), 90),
                StrengthSlot(1, 4, ProgramSlotType.TIME, listOf(30, 30), 60),
                StrengthSlot(2, 120, ProgramSlotType.TIME, listOf(30, 30), 60, perSide = true)
            )
        ),
        ProgramWorkout.OutdoorRun(distanceKm = 1.7, walk = true),
        ProgramWorkout.Treadmill(listOf(TreadmillSegment(300, 5.0, 1.0, TreadmillSegmentLabel.WARM_UP))),
        ProgramWorkout.HomeCircuit(3, 30, 30, 60, listOf(1, 2, 3))
    )

    @Test
    fun `every workout kind survives a payload round trip`() {
        workouts.forEach { workout ->
            assertEquals(workout, workout.toPayload().toProgramWorkoutOrNull())
        }
    }

    @Test
    fun `payload carries the kind discriminator`() {
        assertTrue(workouts[1].toPayload().contains("\"kind\":\"OUTDOOR_RUN\""))
    }

    @Test
    fun `payload stored before perSide existed decodes as not per side`() {
        val old = """{"kind":"STRENGTH","title":"FULL_BODY_A","slots":[""" +
            """{"position":0,"exerciseId":120,"type":"TIME","reps":[30,30],"restSeconds":60}]}"""

        val workout = old.toProgramWorkoutOrNull() as ProgramWorkout.Strength

        assertEquals(false, workout.slots.single().perSide)
    }

    @Test
    fun `unreadable payload maps to null`() {
        assertNull("""{"kind":"YOGA"}""".toProgramWorkoutOrNull())
        assertNull("not json".toProgramWorkoutOrNull())
    }

    @Test
    fun `workout entities are numbered by week and day`() {
        val program = GeneratedProgram(
            listOf(
                ProgramWeek(1, false, workouts.take(2)),
                ProgramWeek(2, true, workouts.take(2))
            )
        )

        val entities = program.toWorkoutEntities(programId = 7L)

        assertEquals(listOf(1 to 0, 1 to 1, 2 to 0, 2 to 1), entities.map { it.week to it.dayIndex })
        assertEquals(listOf("STRENGTH", "OUTDOOR_RUN", "STRENGTH", "OUTDOOR_RUN"), entities.map { it.kind })
        assertEquals(listOf(false, false, true, true), entities.map { it.isDeload })
        assertTrue(entities.all { it.programId == 7L })
    }

    @Test
    fun `profile round trip through the entity`() {
        val profile = ProgramProfile(45, 82.5, 180, ProgramLevel.ADVANCED, ProgramGoal.LOSE_FAT, ProgramLocation.HOME, 8, 4)

        assertEquals(profile, profile.toEntity(createdAt = 5L).toProfile())
    }

    @Test
    fun `focus round trip through the entity, stored by code`() {
        ProgramFocus.entries.forEach { focus ->
            val profile = ProgramProfile(
                30, 70.0, 175, ProgramLevel.BEGINNER, ProgramGoal.GAIN_MUSCLE, ProgramLocation.GYM, 6, 3, focus
            )

            val entity = profile.toEntity(createdAt = 5L)

            assertEquals(focus.code, entity.focus)
            assertEquals(profile, entity.toProfile())
        }
    }

    @Test
    fun `an entity without a focus or with an unknown one reads as balanced`() {
        val profile = ProgramProfile(30, 70.0, 175, ProgramLevel.BEGINNER, ProgramGoal.GAIN_MUSCLE, ProgramLocation.GYM, 6, 3)
        val entity = profile.toEntity(createdAt = 5L)

        assertEquals(ProgramFocus.BALANCED, entity.copy(focus = "BALANCED").toProfile().focus)
        assertEquals(ProgramFocus.BALANCED, entity.copy(focus = "LEGS_ONLY").toProfile().focus)
    }
}
