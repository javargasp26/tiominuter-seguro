package com.javargasp.tiominutermvi.ui.components

import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.testutil.StringResources
import com.javargasp.tiominutermvi.testutil.XmlWorkoutNameTexts
import com.javargasp.tiominutermvi.testutil.dailyRoutine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The workout-name strings of Home and the daily selection, in both languages. */
class WorkoutNameStringsTest {

    private val keys = listOf("workout_name_day", "workout_name_day_with_title", "home_day_available")

    @Test
    fun `both languages define the workout-name strings with the same placeholders`() {
        val placeholder = Regex("%\\d\\$[sd]")
        keys.forEach { key ->
            val en = StringResources.ENGLISH.raw(key)
            val es = StringResources.SPANISH.raw(key)
            assertEquals(key, placeholder.findAll(en).map { it.value }.toList(), placeholder.findAll(es).map { it.value }.toList())
        }
    }

    @Test
    fun `Home shows the day available in each language`() {
        val day = WorkoutNameResolver.dailyName(dailyRoutine(12))
        assertEquals("Day 12 available", StringResources.ENGLISH.get(R.string.home_day_available, day.text(XmlWorkoutNameTexts.EN)))
        assertEquals("Día 12 disponible", StringResources.SPANISH.get(R.string.home_day_available, day.text(XmlWorkoutNameTexts.ES)))
    }

    @Test
    fun `the daily selection names each routine in each language`() {
        val plain = WorkoutNameResolver.dailyName(dailyRoutine(3))
        assertEquals("Day 3", plain.text(XmlWorkoutNameTexts.EN))
        assertEquals("Día 3", plain.text(XmlWorkoutNameTexts.ES))

        val titled = WorkoutNameResolver.dailyName(dailyRoutine(4, title = "Core", titleEs = "Abdomen"))
        assertEquals("Day 4 · Core", titled.text(XmlWorkoutNameTexts.EN))
        assertEquals("Día 4 · Abdomen", titled.text(XmlWorkoutNameTexts.ES))

        val englishOnly = WorkoutNameResolver.dailyName(dailyRoutine(5, title = "Mobility"))
        assertEquals("a Spanish screen never shows an English title", "Día 5", englishOnly.text(XmlWorkoutNameTexts.ES))
    }

    @Test
    fun `Home and the daily selection format no workout name of their own`() {
        listOf(
            "src/main/java/com/javargasp/tiominutermvi/ui/home/HomeScreen.kt",
            "src/main/java/com/javargasp/tiominutermvi/ui/dailyroutineselection/DailyRoutineSelectionScreen.kt"
        ).forEach { path ->
            // Code only: comments may describe the text
            val source = File(path).readLines().filterNot { it.trimStart().run { startsWith("*") || startsWith("/*") || startsWith("//") } }
                .joinToString("\n")
            assertTrue(path, source.contains("WorkoutName") || source.contains(".text()"))
            assertFalse(path, source.contains("\"Day "))
            assertFalse(path, source.contains("routineDay"))
        }
    }
}
