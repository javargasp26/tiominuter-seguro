package com.javargasp.tiominutermvi.domain.serialization

import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.Routine
import com.javargasp.tiominutermvi.domain.model.RoutineExercise
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals

class SerializationTest {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    @Test
    fun `serialize Exercise correctly`() {
        val exercise = Exercise(
            exerciseId = 1,
            name = "Jumping Jacks",
            animation = "jumping_jack.gif"
        )
        
        val jsonString = json.encodeToString(exercise)
        
        // Parse it back to verify it's valid
        val parsed = json.decodeFromString<Exercise>(jsonString)
        assertEquals(exercise, parsed)
    }
    
    @Test
    fun `deserialize Exercise correctly`() {
        val jsonString = """
            {
                "exerciseId": 1,
                "name": "Jumping Jacks",
                "animation": "jumping_jack.gif"
            }
        """.trimIndent()
        
        val exercise = json.decodeFromString<Exercise>(jsonString)
        
        assertEquals(1, exercise.exerciseId)
        assertEquals("Jumping Jacks", exercise.name)
        assertEquals("jumping_jack.gif", exercise.animation)
    }
    
    @Test
    fun `serialize RoutineExercise with TIME type correctly`() {
        val routineExercise = RoutineExercise(
            exerciseId = 1,
            type = ExerciseType.TIME,
            amount = 60
        )
        
        val jsonString = json.encodeToString(routineExercise)
        
        // Parse it back to verify it's valid
        val parsed = json.decodeFromString<RoutineExercise>(jsonString)
        assertEquals(routineExercise, parsed)
    }
    
    @Test
    fun `serialize RoutineExercise with REPS type correctly`() {
        val routineExercise = RoutineExercise(
            exerciseId = 2,
            type = ExerciseType.REPS,
            amount = 15
        )
        
        val jsonString = json.encodeToString(routineExercise)
        
        // Parse it back to verify it's valid
        val parsed = json.decodeFromString<RoutineExercise>(jsonString)
        assertEquals(routineExercise, parsed)
    }
    
    @Test
    fun `deserialize RoutineExercise correctly`() {
        val jsonString = """
            {
                "exerciseId": 2,
                "type": "REPS",
                "amount": 15
            }
        """.trimIndent()
        
        val routineExercise = json.decodeFromString<RoutineExercise>(jsonString)
        
        assertEquals(2, routineExercise.exerciseId)
        assertEquals(ExerciseType.REPS, routineExercise.type)
        assertEquals(15, routineExercise.amount)
    }
    
    @Test
    fun `serialize Routine correctly`() {
        val routine = Routine(
            routineId = 1,
            routineDay = "Day 1",
            title = "Starter Core",
            exercises = listOf(
                RoutineExercise(
                    exerciseId = 1,
                    type = ExerciseType.TIME,
                    amount = 60
                ),
                RoutineExercise(
                    exerciseId = 2,
                    type = ExerciseType.REPS,
                    amount = 15
                )
            )
        )
        
        val jsonString = json.encodeToString(routine)
        
        // Parse it back to verify it's valid
        val parsed = json.decodeFromString<Routine>(jsonString)
        assertEquals(routine, parsed)
    }
    
    @Test
    fun `deserialize Routine correctly`() {
        val jsonString = """
            {
                "routineId": 1,
                "routineDay": "Day 1",
                "title": "Starter Core",
                "exercises": [
                    {
                        "exerciseId": 1,
                        "type": "TIME",
                        "amount": 60
                    },
                    {
                        "exerciseId": 2,
                        "type": "REPS",
                        "amount": 15
                    }
                ]
            }
        """.trimIndent()
        
        val routine = json.decodeFromString<Routine>(jsonString)
        
        assertEquals(1, routine.routineId)
        assertEquals("Day 1", routine.routineDay)
        assertEquals("Starter Core", routine.title)
        assertEquals(2, routine.exercises.size)
        
        val firstExercise = routine.exercises[0]
        assertEquals(1, firstExercise.exerciseId)
        assertEquals(ExerciseType.TIME, firstExercise.type)
        assertEquals(60, firstExercise.amount)
        
        val secondExercise = routine.exercises[1]
        assertEquals(2, secondExercise.exerciseId)
        assertEquals(ExerciseType.REPS, secondExercise.type)
        assertEquals(15, secondExercise.amount)
    }
}