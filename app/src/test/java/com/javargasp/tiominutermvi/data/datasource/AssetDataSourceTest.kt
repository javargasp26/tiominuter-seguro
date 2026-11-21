package com.javargasp.tiominutermvi.data.datasource

import android.content.Context
import com.javargasp.tiominutermvi.domain.model.Exercise
import com.javargasp.tiominutermvi.domain.model.Routine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class AssetDataSourceTest {
    
    private lateinit var context: Context
    private lateinit var assetManager: android.content.res.AssetManager
    private lateinit var assetDataSource: AssetDataSource
    private val json = Json { ignoreUnknownKeys = true }
    
    @Before
    fun setup() {
        context = mockk()
        assetManager = mockk()
        assetDataSource = AssetDataSource(context, json)
        
        every { context.assets } returns assetManager
    }
    
    @Test
    fun `load exercises from assets successfully`() = runTest {
        // Given
        val exercisesJson = """
            [
                { "exerciseId": 1, "name": "Jumping Jacks", "animation": "1_jumping_jack.gif" },
                { "exerciseId": 2, "name": "Squats", "animation": "2_squat.gif" }
            ]
        """.trimIndent()
        
        val inputStream: InputStream = ByteArrayInputStream(exercisesJson.toByteArray())
        every { assetManager.open("exercises.json") } returns inputStream
        
        // When
        val exercises = assetDataSource.loadFromAssets(
            "exercises.json",
            kotlinx.serialization.builtins.ListSerializer(Exercise.serializer())
        )
        
        // Then
        assertEquals(2, exercises.size)
        assertEquals(1, exercises[0].exerciseId)
        assertEquals("Jumping Jacks", exercises[0].name)
        assertEquals("1_jumping_jack.gif", exercises[0].animation)
        assertEquals(2, exercises[1].exerciseId)
        assertEquals("Squats", exercises[1].name)
        assertEquals("2_squat.gif", exercises[1].animation)
    }
    
    @Test
    fun `load routines from assets successfully`() = runTest {
        // Given
        val routinesJson = """
            [
                {
                    "routineId": 1,
                    "routineDay": "Day 1",
                    "exercises": [
                        { "exerciseId": 1, "type": "TIME", "amount": 60 },
                        { "exerciseId": 2, "type": "REPS", "amount": 15 }
                    ]
                }
            ]
        """.trimIndent()
        
        val inputStream: InputStream = ByteArrayInputStream(routinesJson.toByteArray())
        every { assetManager.open("routines.json") } returns inputStream
        
        // When
        val routines = assetDataSource.loadFromAssets(
            "routines.json",
            kotlinx.serialization.builtins.ListSerializer(Routine.serializer())
        )
        
        // Then
        assertEquals(1, routines.size)
        val routine = routines[0]
        assertEquals(1, routine.routineId)
        assertEquals("Day 1", routine.routineDay)
        assertEquals(2, routine.exercises.size)
        
        val firstExercise = routine.exercises[0]
        assertEquals(1, firstExercise.exerciseId)
        assertEquals(com.javargasp.tiominutermvi.domain.model.ExerciseType.TIME, firstExercise.type)
        assertEquals(60, firstExercise.amount)
        
        val secondExercise = routine.exercises[1]
        assertEquals(2, secondExercise.exerciseId)
        assertEquals(com.javargasp.tiominutermvi.domain.model.ExerciseType.REPS, secondExercise.type)
        assertEquals(15, secondExercise.amount)
    }
}