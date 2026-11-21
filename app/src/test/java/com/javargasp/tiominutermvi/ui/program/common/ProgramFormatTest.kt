package com.javargasp.tiominutermvi.ui.program.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ProgramFormatTest {

    private val english = Locale.US
    private val spanish = Locale.forLanguageTag("es-ES")

    @Test
    fun `percentages have one decimal`() {
        assertEquals("0.0", formatPercent(0, 3, english))
        assertEquals("33.3", formatPercent(1, 3, english))
        assertEquals("66.7", formatPercent(2, 3, english))
        assertEquals("100.0", formatPercent(3, 3, english))
    }

    @Test
    fun `spanish uses a decimal comma`() {
        assertEquals("33,3", formatPercent(1, 3, spanish))
        assertEquals("66,7", formatPercent(2, 3, spanish))
        assertEquals("100,0", formatPercent(3, 3, spanish))
    }

    @Test
    fun `no workouts is zero percent`() {
        assertEquals("0.0", formatPercent(0, 0, english))
    }

    @Test
    fun `one decimal for distances and speeds`() {
        assertEquals("1.8", formatOneDecimal(1.815, english))
        assertEquals("8,5", formatOneDecimal(8.5, spanish))
    }

    @Test
    fun `exercise summary for reps and time`() {
        assertEquals("Sets: 4 · Reps: 10, 10, 10, 10", programExerciseSummary(false, listOf(10, 10, 10, 10), englishLabels))
        assertEquals("Sets: 2 · Time: 30s, 45s", programExerciseSummary(true, listOf(30, 45), englishLabels))
    }

    @Test
    fun `per-side summary in English`() {
        assertEquals(
            "Sets: 3 · Time: 40s, 40s, 40s per side",
            programExerciseSummary(true, listOf(40, 40, 40), englishLabels, perSide = true)
        )
        assertEquals(
            "Sets: 3 · Reps: 10, 10, 10 per side",
            programExerciseSummary(false, listOf(10, 10, 10), englishLabels, perSide = true)
        )
    }

    @Test
    fun `per-side summary in Spanish`() {
        assertEquals(
            "Series: 3 · Tiempo: 40 s, 40 s, 40 s por lado",
            programExerciseSummary(true, listOf(40, 40, 40), spanishLabels, perSide = true)
        )
        assertEquals(
            "Series: 3 · Reps: 10, 10, 10 por lado",
            programExerciseSummary(false, listOf(10, 10, 10), spanishLabels, perSide = true)
        )
        assertEquals("Series: 2 · Reps: 8, 8", programExerciseSummary(false, listOf(8, 8), spanishLabels))
    }

    // Same values as the program_summary_*, program_per_side and program_seconds_short strings.
    private val englishLabels = ProgramSummaryLabels("Sets", "Reps", "Time", "per side") { "${it}s" }
    private val spanishLabels = ProgramSummaryLabels("Series", "Reps", "Tiempo", "por lado") { "$it s" }
}
