package com.javargasp.tiominutermvi.ui.components

import com.javargasp.tiominutermvi.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseSummaryTest {

    // Mirrors values/strings.xml.
    private val english = ExerciseSummaryLabels(
        sets = { "Sets: $it" },
        reps = { "Reps: $it" },
        time = { "Time: $it" },
        seconds = { "${it}s" },
        join = { sets, values -> "$sets · $values" }
    )

    // Mirrors values-es/strings.xml.
    private val spanish = ExerciseSummaryLabels(
        sets = { "Series: $it" },
        reps = { "Repeticiones: $it" },
        time = { "Tiempo: $it" },
        seconds = { "$it s" },
        join = { sets, values -> "$sets · $values" }
    )

    @Test
    fun `REPS lists every set`() {
        assertEquals(
            "Sets: 4 · Reps: 10, 10, 10, 10",
            exerciseSummary(ExerciseType.REPS, listOf(10, 10, 10, 10), english)
        )
    }

    @Test
    fun `TIME lists every set in seconds`() {
        assertEquals(
            "Sets: 2 · Time: 30s, 45s",
            exerciseSummary(ExerciseType.TIME, listOf(30, 45), english)
        )
    }

    @Test
    fun `single REPS set`() {
        assertEquals(
            "Sets: 1 · Reps: 12",
            exerciseSummary(ExerciseType.REPS, listOf(12), english)
        )
    }

    @Test
    fun `single TIME set`() {
        assertEquals(
            "Sets: 1 · Time: 60s",
            exerciseSummary(ExerciseType.TIME, listOf(60), english)
        )
    }

    @Test
    fun `uneven reps keep their order`() {
        assertEquals(
            "Sets: 3 · Reps: 12, 10, 8",
            exerciseSummary(ExerciseType.REPS, listOf(12, 10, 8), english)
        )
    }

    @Test
    fun `labels come from the locale`() {
        assertEquals(
            "Series: 3 · Tiempo: 30 s, 45 s, 60 s",
            exerciseSummary(ExerciseType.TIME, listOf(30, 45, 60), spanish)
        )
        assertEquals(
            "Series: 3 · Repeticiones: 12, 10, 8",
            exerciseSummary(ExerciseType.REPS, listOf(12, 10, 8), spanish)
        )
    }
}
