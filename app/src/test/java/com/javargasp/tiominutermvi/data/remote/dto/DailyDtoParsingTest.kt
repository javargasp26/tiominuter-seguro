package com.javargasp.tiominutermvi.data.remote.dto

import com.javargasp.tiominutermvi.data.remote.remoteJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyDtoParsingTest {

    private val json = remoteJson

    @Test
    fun `parse routine without title`() {
        val jsonString = """
            {
                "routineId": 1,
                "routineDay": "Day 1",
                "exercises": [
                    { "exerciseId": 2, "type": "REPS", "amount": 15 }
                ]
            }
        """.trimIndent()

        val routine = json.decodeFromString(DailyRoutineDto.serializer(), jsonString)

        assertEquals(1, routine.routineId)
        assertEquals("Day 1", routine.routineDay)
        assertNull(routine.title)
        assertEquals(listOf(DailyRoutineExerciseDto(2, "REPS", 15)), routine.exercises)
    }

    @Test
    fun `parse routine with title`() {
        val jsonString = """
            {
                "routineId": 2,
                "routineDay": "Day 2",
                "title": "Legs",
                "exercises": [
                    { "exerciseId": 9, "type": "TIME", "amount": 60 }
                ]
            }
        """.trimIndent()

        val routine = json.decodeFromString(DailyRoutineDto.serializer(), jsonString)

        assertEquals("Legs", routine.title)
        assertEquals(listOf(DailyRoutineExerciseDto(9, "TIME", 60)), routine.exercises)
    }

    @Test
    fun `parse routines with REPS and TIME exercises in order`() {
        val jsonString = """
            [
                {
                    "routineId": 1,
                    "routineDay": "Day 1",
                    "exercises": [
                        { "exerciseId": 1, "type": "TIME", "amount": 60 },
                        { "exerciseId": 2, "type": "REPS", "amount": 15 },
                        { "exerciseId": 13, "type": "TIME", "amount": 30 }
                    ]
                }
            ]
        """.trimIndent()

        val routines = json.decodeFromString(ListSerializer(DailyRoutineDto.serializer()), jsonString)

        assertEquals(1, routines.size)
        assertEquals(
            listOf(
                DailyRoutineExerciseDto(exerciseId = 1, type = "TIME", amount = 60),
                DailyRoutineExerciseDto(exerciseId = 2, type = "REPS", amount = 15),
                DailyRoutineExerciseDto(exerciseId = 13, type = "TIME", amount = 30)
            ),
            routines[0].exercises
        )
    }

    @Test
    fun `type is kept as the raw string`() {
        val jsonString = """
            { "exerciseId": 3, "type": "UNKNOWN", "amount": 10 }
        """.trimIndent()

        val exercise = json.decodeFromString(DailyRoutineExerciseDto.serializer(), jsonString)

        assertEquals("UNKNOWN", exercise.type)
    }
}
