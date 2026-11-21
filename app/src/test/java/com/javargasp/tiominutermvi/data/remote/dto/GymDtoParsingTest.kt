package com.javargasp.tiominutermvi.data.remote.dto

import com.javargasp.tiominutermvi.data.remote.remoteJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GymDtoParsingTest {

    private val json = remoteJson

    @Test
    fun `set and rest map to sets and restSeconds`() {
        val jsonString = """
            { "exerciseId": 18, "type": "REPS", "set": 4, "reps": [10, 10, 10, 10], "rest": 45 }
        """.trimIndent()

        val exercise = json.decodeFromString(GymRoutineExerciseDto.serializer(), jsonString)

        assertEquals(18, exercise.exerciseId)
        assertEquals(4, exercise.sets)
        assertEquals(listOf(10, 10, 10, 10), exercise.reps)
        assertEquals(45, exercise.restSeconds)
    }

    @Test
    fun `parse routine with REPS and TIME exercises`() {
        val jsonString = """
            [
                {
                    "routineId": 64,
                    "routineName": "Pull Day",
                    "exercises": [
                        { "exerciseId": 18, "type": "REPS", "set": 2, "reps": [12, 10], "rest": 45 },
                        { "exerciseId": 4, "type": "TIME", "set": 3, "reps": [30, 45, 60], "rest": 20 }
                    ]
                }
            ]
        """.trimIndent()

        val routines = json.decodeFromString(ListSerializer(GymRoutineDto.serializer()), jsonString)

        assertEquals(1, routines.size)
        val routine = routines[0]
        assertEquals(64, routine.routineId)
        assertEquals("Pull Day", routine.routineName)
        assertEquals(
            GymRoutineExerciseDto(exerciseId = 18, type = "REPS", sets = 2, reps = listOf(12, 10), restSeconds = 45),
            routine.exercises[0]
        )
        assertEquals(
            GymRoutineExerciseDto(exerciseId = 4, type = "TIME", sets = 3, reps = listOf(30, 45, 60), restSeconds = 20),
            routine.exercises[1]
        )
    }

    @Test
    fun `comments and trailing commas are accepted`() {
        val jsonString = """
            // Gym routines
            [
                {
                    "routineId": 65, /* push */
                    "routineName": "Push Day",
                    "exercises": [
                        { "exerciseId": 85, "type": "REPS", "set": 1, "reps": [12,], "rest": 30, },
                    ],
                },
            ]
        """.trimIndent()

        val routines = json.decodeFromString(ListSerializer(GymRoutineDto.serializer()), jsonString)

        assertEquals(1, routines.size)
        assertEquals("Push Day", routines[0].routineName)
        assertEquals(listOf(12), routines[0].exercises[0].reps)
    }

    @Test
    fun `parse exercise catalog entry`() {
        val jsonString = """
            { "exerciseId": 18, "name": "Pull-ups", "animation": "18_pull_ups.gif" }
        """.trimIndent()

        val exercise = json.decodeFromString(ExerciseDto.serializer(), jsonString)

        assertEquals(ExerciseDto(18, "Pull-ups", "18_pull_ups.gif"), exercise)
        assertNull(exercise.nameEs)
    }

    @Test
    fun `exercise catalog entry with a Spanish name`() {
        val jsonString = """
            { "exerciseId": 18, "name": "Pull-ups", "nameEs": "Dominadas", "animation": "18_pull_ups.gif" }
        """.trimIndent()

        val exercise = json.decodeFromString(ExerciseDto.serializer(), jsonString)

        assertEquals(ExerciseDto(18, "Pull-ups", "18_pull_ups.gif", nameEs = "Dominadas"), exercise)
    }

    @Test
    fun `routine with and without a Spanish name`() {
        val jsonString = """
            [
                { "routineId": 64, "routineName": "Pull Day", "routineNameEs": "Día de tirón", "exercises": [] },
                { "routineId": 65, "routineName": "Push Day", "exercises": [] }
            ]
        """.trimIndent()

        val routines = json.decodeFromString(ListSerializer(GymRoutineDto.serializer()), jsonString)

        assertEquals("Día de tirón", routines[0].routineNameEs)
        assertNull(routines[1].routineNameEs)
        assertEquals("Push Day", routines[1].routineName)
    }
}
