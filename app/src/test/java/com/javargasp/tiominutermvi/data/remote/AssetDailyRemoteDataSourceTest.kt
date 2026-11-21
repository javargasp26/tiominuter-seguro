package com.javargasp.tiominutermvi.data.remote

import com.javargasp.tiominutermvi.data.content.ContentSource
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineDto
import com.javargasp.tiominutermvi.data.remote.dto.DailyRoutineExerciseDto
import com.javargasp.tiominutermvi.data.remote.dto.ExerciseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class AssetDailyRemoteDataSourceTest {

    private lateinit var contentSource: ContentSource
    private lateinit var remoteDataSource: AssetDailyRemoteDataSource

    @Before
    fun setup() {
        contentSource = mockk()
        remoteDataSource = AssetDailyRemoteDataSource(contentSource)
    }

    @Test
    fun `fetchDailyRoutines returns parsed routines`() = runTest {
        // Given
        coEvery { contentSource.readText("routines.json") } returns """
            [
                {
                    "routineId": 1,
                    "routineDay": "Day 1",
                    "exercises": [
                        { "exerciseId": 1, "type": "TIME", "amount": 60 },
                        { "exerciseId": 2, "type": "REPS", "amount": 15 }
                    ]
                },
                {
                    "routineId": 2,
                    "routineDay": "Day 2",
                    "title": "Legs",
                    "exercises": [
                        { "exerciseId": 9, "type": "TIME", "amount": 60 }
                    ]
                }
            ]
        """.trimIndent()

        // When
        val routines = remoteDataSource.fetchDailyRoutines()

        // Then
        assertEquals(
            listOf(
                DailyRoutineDto(
                    routineId = 1,
                    routineDay = "Day 1",
                    exercises = listOf(
                        DailyRoutineExerciseDto(1, "TIME", 60),
                        DailyRoutineExerciseDto(2, "REPS", 15)
                    )
                ),
                DailyRoutineDto(
                    routineId = 2,
                    routineDay = "Day 2",
                    title = "Legs",
                    exercises = listOf(DailyRoutineExerciseDto(9, "TIME", 60))
                )
            ),
            routines
        )
    }

    @Test
    fun `fetchExercises returns parsed exercises`() = runTest {
        // Given
        coEvery { contentSource.readText("exercises.json") } returns """
            [
                { "exerciseId": 1, "name": "Jumping Jacks", "animation": "1_jumping_jack.gif" },
                { "exerciseId": 13, "name": "Rest", "animation": "13_rest.gif" }
            ]
        """.trimIndent()

        // When
        val exercises = remoteDataSource.fetchExercises()

        // Then
        assertEquals(
            listOf(
                ExerciseDto(1, "Jumping Jacks", "1_jumping_jack.gif"),
                ExerciseDto(13, "Rest", "13_rest.gif")
            ),
            exercises
        )
    }

    @Test(expected = IOException::class)
    fun `fetchDailyRoutines propagates read errors`() = runTest {
        coEvery { contentSource.readText("routines.json") } throws IOException("missing")

        remoteDataSource.fetchDailyRoutines()
    }

    @Test(expected = IOException::class)
    fun `fetchExercises propagates read errors`() = runTest {
        coEvery { contentSource.readText("exercises.json") } throws IOException("missing")

        remoteDataSource.fetchExercises()
    }
}
