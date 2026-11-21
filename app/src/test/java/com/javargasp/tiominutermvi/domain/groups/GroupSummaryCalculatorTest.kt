package com.javargasp.tiominutermvi.domain.groups

import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource.ACTIVITY
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource.DAILY
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource.GYM
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource.PROGRAM
import com.javargasp.tiominutermvi.testutil.trainingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The week of Monday 19 October 2026 (Sunday 25). */
class GroupSummaryCalculatorTest {

    private val goals = listOf(WeeklyGoal(3, "2026-10-05"))

    @Test
    fun `no goal this week means no summary`() {
        assertNull(GroupSummaryCalculator.summary("2026-10-22", listOf(trainingEntry("2026-10-22")), emptyList()))
        // A goal set only next week doesn't count yet
        assertNull(GroupSummaryCalculator.summary("2026-10-22", emptyList(), listOf(WeeklyGoal(3, "2026-10-26"))))
    }

    @Test
    fun `the week runs Monday to Sunday, on Monday and on Sunday`() {
        val entries = listOf(
            trainingEntry("2026-10-18"), // previous Sunday
            trainingEntry("2026-10-19"),
            trainingEntry("2026-10-25"),
            trainingEntry("2026-10-26") // next Monday
        )
        val onMonday = GroupSummaryCalculator.summary("2026-10-19", entries, goals)!!
        val onSunday = GroupSummaryCalculator.summary("2026-10-25", entries, goals)!!

        assertEquals("2026-10-19", onMonday.weekStartLocal)
        assertEquals("2026-10-19", onSunday.weekStartLocal)
        assertEquals(2, onSunday.workoutsThisWeek)
        assertEquals(setOf(1, 7), onSunday.daysThisWeek.keys)
        assertEquals("2026-10-26", GroupSummaryCalculator.summary("2026-10-26", entries, goals)!!.weekStartLocal)
    }

    @Test
    fun `only workouts of at least 10 minutes, or of unknown duration, count`() {
        val entries = listOf(
            trainingEntry("2026-10-20", minutes = 9),
            trainingEntry("2026-10-20", DAILY, minutes = 10),
            trainingEntry("2026-10-21", ACTIVITY, minutes = null)
        )
        val summary = GroupSummaryCalculator.summary("2026-10-22", entries, goals)!!

        assertEquals(2, summary.workoutsThisWeek)
        assertEquals(mapOf(2 to listOf(DAILY), 3 to listOf(ACTIVITY)), summary.daysThisWeek)
        assertEquals("2026-10-21", summary.lastTrainedLocal)
    }

    @Test
    fun `each day lists distinct sources, at most the 4, in a fixed order`() {
        val entries = listOf(ACTIVITY, GYM, GYM, PROGRAM, DAILY, GYM).mapIndexed { i, source ->
            trainingEntry("2026-10-21", source, id = i.toLong())
        }
        val summary = GroupSummaryCalculator.summary("2026-10-22", entries, goals)!!

        assertEquals(listOf(DAILY, GYM, PROGRAM, ACTIVITY), summary.daysThisWeek[3])
        assertEquals(6, summary.workoutsThisWeek)
    }

    @Test
    fun `lastTrainedLocal is the latest counted date, also from an earlier week, never in the future`() {
        val entries = listOf(
            trainingEntry("2026-10-10"),
            trainingEntry("2026-10-21", minutes = 5),
            trainingEntry("2026-10-30") // a wrong clock
        )
        assertEquals("2026-10-10", GroupSummaryCalculator.summary("2026-10-22", entries, goals)!!.lastTrainedLocal)
        assertNull(GroupSummaryCalculator.summary("2026-10-22", emptyList(), goals)!!.lastTrainedLocal)
    }

    @Test
    fun `goal and streak come from the weekly goal rules`() {
        // Goal 2: met the two previous weeks and this week
        val entries = listOf("2026-10-05", "2026-10-06", "2026-10-12", "2026-10-13", "2026-10-19", "2026-10-20")
            .map { trainingEntry(it) }
        val summary = GroupSummaryCalculator.summary("2026-10-22", entries, listOf(WeeklyGoal(2, "2026-10-05")))!!

        assertEquals(2, summary.weeklyGoal)
        assertEquals(3, summary.streakWeeks)
    }

    @Test
    fun `workouts are capped at 100`() {
        val entries = (0 until 120).map { trainingEntry("2026-10-21", id = it.toLong()) }
        assertEquals(100, GroupSummaryCalculator.summary("2026-10-22", entries, goals)!!.workoutsThisWeek)
    }
}
