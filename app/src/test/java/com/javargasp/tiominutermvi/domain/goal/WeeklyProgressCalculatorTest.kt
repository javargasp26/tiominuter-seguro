package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Dates: 19 October 2026 is a Monday; the current week in most tests is 19–25 October.
 * Inputs are counted workouts per date (see WorkoutCountingTest for the counting rule).
 */
class WeeklyProgressCalculatorTest {

    private val calc = WeeklyProgressCalculator

    /** One counted workout on each date. */
    private fun once(vararg dates: String): Map<String, Int> = dates.associateWith { 1 }

    // ------------------------------------------------------------------
    // Goal history
    // ------------------------------------------------------------------

    @Test
    fun `no goal before the first one`() {
        assertNull(calc.goalForWeek(emptyList(), "2026-10-19"))
        assertNull(calc.goalForWeek(listOf(WeeklyGoal(3, "2026-10-19")), "2026-10-12"))
    }

    @Test
    fun `the latest goal on or before the week applies, in any list order`() {
        val goals = listOf(WeeklyGoal(5, "2026-11-02"), WeeklyGoal(3, "2026-10-05"), WeeklyGoal(4, "2026-10-19"))

        assertEquals(3, calc.goalForWeek(goals, "2026-10-05"))
        assertEquals(3, calc.goalForWeek(goals, "2026-10-12"))
        assertEquals(4, calc.goalForWeek(goals, "2026-10-19"))
        assertEquals(4, calc.goalForWeek(goals, "2026-10-26"))
        assertEquals(5, calc.goalForWeek(goals, "2026-11-09"))
    }

    @Test
    fun `a goal changed mid-history keeps the old goal for past weeks`() {
        val goals = listOf(WeeklyGoal(2, "2026-10-05"), WeeklyGoal(4, "2026-10-19"))
        val workouts = once("2026-10-06", "2026-10-08", "2026-10-20", "2026-10-21")

        assertTrue(calc.progress("2026-10-07", goals, workouts).isGoalMet) // 2 of 2
        assertFalse(calc.progress("2026-10-22", goals, workouts).isGoalMet) // 2 of 4
        assertEquals(4, calc.progress("2026-10-22", goals, workouts).goal)
    }

    // ------------------------------------------------------------------
    // Weekly progress
    // ------------------------------------------------------------------

    @Test
    fun `several workouts on the same day count separately`() {
        val goals = listOf(WeeklyGoal(3, "2026-10-19"))

        val progress = calc.progress("2026-10-22", goals, mapOf("2026-10-19" to 2, "2026-10-21" to 1))

        assertEquals(3, progress.workoutCount)
        assertEquals(2, progress.workoutsOn("2026-10-19"))
        assertEquals(0, progress.workoutsOn("2026-10-20"))
        assertEquals(setOf("2026-10-19", "2026-10-21"), progress.trainedDates)
        assertTrue(progress.isGoalMet)
    }

    @Test
    fun `progress keeps only the dates of that week`() {
        val workouts = mapOf("2026-10-18" to 3, "2026-10-19" to 1, "2026-10-25" to 2, "2026-10-26" to 4)

        val progress = calc.progress("2026-10-22", listOf(WeeklyGoal(3, "2026-10-19")), workouts)

        assertEquals("2026-10-19", progress.weekStart)
        assertEquals(mapOf("2026-10-19" to 1, "2026-10-25" to 2), progress.workoutsPerDay)
        assertEquals(3, progress.workoutCount)
    }

    @Test
    fun `exceeding the goal is still met and keeps the real count`() {
        val progress = calc.progress("2026-10-22", listOf(WeeklyGoal(4, "2026-10-19")), mapOf("2026-10-19" to 3, "2026-10-20" to 2))

        assertEquals(5, progress.workoutCount)
        assertTrue(progress.isGoalMet)
    }

    @Test
    fun `Sunday belongs to its week and Monday starts the next one`() {
        val goals = listOf(WeeklyGoal(1, "2026-10-12"))

        assertTrue(calc.progress("2026-10-19", goals, once("2026-10-25")).isGoalMet)
        assertFalse(calc.progress("2026-10-19", goals, once("2026-10-26")).isGoalMet)
        assertFalse(calc.progress("2026-10-19", goals, once("2026-10-18")).isGoalMet)
    }

    @Test
    fun `the device zone at recording time decides the week near midnight`() {
        // A workout finished at 2026-10-25T23:30:00Z
        val completedAt = 1_792_971_000_000L
        val utcDate = LocalDateProvider({ completedAt }, { TimeZone.getTimeZone("UTC") }).dateOf(completedAt)
        val madridDate = LocalDateProvider({ completedAt }, { TimeZone.getTimeZone("Europe/Madrid") }).dateOf(completedAt)
        val goals = listOf(WeeklyGoal(1, "2026-10-12"))

        assertTrue(calc.progress("2026-10-19", goals, once(utcDate)).isGoalMet) // Sunday 25
        assertTrue(calc.progress("2026-10-26", goals, once(madridDate)).isGoalMet) // Monday 26
        assertFalse(calc.progress("2026-10-19", goals, once(madridDate)).isGoalMet)
    }

    @Test
    fun `without a goal the week is never met`() {
        val progress = calc.progress("2026-10-19", emptyList(), mapOf("2026-10-19" to 3))

        assertNull(progress.goal)
        assertEquals(3, progress.workoutCount)
        assertFalse(progress.isGoalMet)
    }

    // ------------------------------------------------------------------
    // Streak (unchanged rules, counted in workouts)
    // ------------------------------------------------------------------

    @Test
    fun `no goal means no streak`() {
        assertEquals(0, calc.streak("2026-10-22", emptyList(), once("2026-10-19", "2026-10-12")))
    }

    @Test
    fun `completed weeks with the goal met count back until one is missed`() {
        val goals = listOf(WeeklyGoal(1, "2026-09-28"))
        // Met: weeks of 28 Sep, 5 Oct and 12 Oct; the current week has nothing yet
        val workouts = once("2026-09-29", "2026-10-06", "2026-10-13")

        assertEquals(3, calc.streak("2026-10-22", goals, workouts))
    }

    @Test
    fun `two workouts in one day can meet a goal of two`() {
        val goals = listOf(WeeklyGoal(2, "2026-10-12"))

        assertEquals(1, calc.streak("2026-10-22", goals, mapOf("2026-10-14" to 2)))
    }

    @Test
    fun `a gap breaks the streak`() {
        val goals = listOf(WeeklyGoal(1, "2026-09-28"))
        // Week of 5 Oct has no workout
        val workouts = once("2026-09-29", "2026-10-13")

        assertEquals(1, calc.streak("2026-10-22", goals, workouts))
    }

    @Test
    fun `an unfinished current week does not break the streak and adds one once met`() {
        val goals = listOf(WeeklyGoal(2, "2026-10-05"))
        val past = once("2026-10-05", "2026-10-07", "2026-10-12", "2026-10-14")

        assertEquals(2, calc.streak("2026-10-22", goals, past))
        assertEquals(2, calc.streak("2026-10-22", goals, past + ("2026-10-20" to 1)))
        assertEquals(3, calc.streak("2026-10-22", goals, past + ("2026-10-20" to 2)))
    }

    @Test
    fun `weeks before the first goal do not count`() {
        val goals = listOf(WeeklyGoal(1, "2026-10-12"))
        val workouts = once("2026-09-29", "2026-10-06", "2026-10-13")

        assertEquals(1, calc.streak("2026-10-22", goals, workouts))
    }

    @Test
    fun `a past week measured against its own higher goal can break the streak`() {
        val goals = listOf(WeeklyGoal(1, "2026-10-05"), WeeklyGoal(3, "2026-10-12"), WeeklyGoal(1, "2026-10-19"))
        val workouts = once("2026-10-06", "2026-10-13", "2026-10-14")

        // Week of 12 Oct: 2 of 3 → missed
        assertEquals(0, calc.streak("2026-10-22", goals, workouts))
    }

    // ------------------------------------------------------------------
    // Current week
    // ------------------------------------------------------------------

    @Test
    fun `the current week summary says how many workouts are needed`() {
        val goals = listOf(WeeklyGoal(5, "2026-10-19"))

        val summary = calc.currentWeek("2026-10-22", goals, mapOf("2026-10-19" to 2))
        assertEquals(3, summary.workoutsNeeded)
        assertEquals(0, summary.streak)

        val exceeded = calc.currentWeek("2026-10-25", listOf(WeeklyGoal(1, "2026-10-19")), mapOf("2026-10-19" to 3))
        assertEquals(0, exceeded.workoutsNeeded)
        assertEquals(1, exceeded.streak)

        assertEquals(0, calc.currentWeek("2026-10-22", emptyList(), emptyMap()).workoutsNeeded)
    }
}
