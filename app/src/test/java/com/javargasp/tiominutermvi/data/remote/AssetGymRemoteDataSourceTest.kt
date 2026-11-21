package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AssetGymRemoteDataSourceTest {

    private lateinit var contentSource: ContentSource
    private lateinit var remoteDataSource: AssetGymRemoteDataSource

    @Before
    fun setup() {
        contentSource = mockk()
        remoteDataSource = AssetGymRemoteDataSource(contentSource)
    }

    @Test
    fun `fetchGymRoutines returns parsed routines`() = runTest {
        // Given
        coEvery { contentSource.readText("routines_for_gym.jsonc") } returns """
            // fake endpoint
            [
                {
                    "routineId": 64,
                    "routineName": "Pull Day",
                    "exercises": [
                        { "exerciseId": 18, "type": "REPS", "set": 4, "reps": [10,10,10,10], "rest": 45 },
                    ]
                }
            ]
        """.trimIndent()

        // When
        val routines = remoteDataSource.fetchGymRoutines()

        // Then
        assertEquals(1, routines.size)
        assertEquals(64, routines[0].routineId)
        assertEquals("Pull Day", routines[0].routineName)
        assertEquals(4, routines[0].exercises[0].sets)
        assertEquals(45, routines[0].exercises[0].restSeconds)
    }

    @Test
    fun `fetchExercises returns parsed exercises`() = runTest {
        // Given
        coEvery { contentSource.readText("exercises.json") } returns """
            [
                { "exerciseId": 1, "name": "Jumping Jacks", "animation": "1_jumping_jack.gif" },
                { "exerciseId": 18, "name": "Pull-ups", "animation": "18_pull_ups.gif" }
            ]
        """.trimIndent()

        // When
        val exercises = remoteDataSource.fetchExercises()

        // Then
        assertEquals(
            listOf(
                ExerciseDto(1, "Jumping Jacks", "1_jumping_jack.gif"),
                ExerciseDto(18, "Pull-ups", "18_pull_ups.gif")
            ),
            exercises
        )
    }

    @Test(expected = IOException::class)
    fun `fetchGymRoutines propagates read errors`() = runTest {
        coEvery { contentSource.readText("routines_for_gym.jsonc") } throws IOException("missing")

        remoteDataSource.fetchGymRoutines()
    }

    @Test(expected = IOException::class)
    fun `fetchExercises propagates read errors`() = runTest {
        coEvery { contentSource.readText("exercises.json") } throws IOException("missing")

        remoteDataSource.fetchExercises()
    }
}
