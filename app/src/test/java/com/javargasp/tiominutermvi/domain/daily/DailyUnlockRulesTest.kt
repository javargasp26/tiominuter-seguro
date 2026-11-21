package com.javargasp.tiominutermvi.domain.daily

import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules.HistoryEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyUnlockRulesTest {

    @Test
    fun `a new person has only Day 1`() {
        val fresh = DailyProgress()

        assertTrue(DailyUnlockRules.isUnlocked(1, fresh))
        assertFalse(DailyUnlockRules.isUnlocked(2, fresh))
        assertFalse(DailyUnlockRules.isCompleted(1, fresh))
        assertEquals(1, DailyUnlockRules.nextAvailableDay(fresh, 30))
    }

    @Test
    fun `finishing day N unlocks day N + 1, and earlier days stay available`() {
        val afterTwo = DailyUnlockRules.afterFinishing(DailyUnlockRules.afterFinishing(DailyProgress(), 1), 2)

        assertEquals(DailyProgress(lastCompletedDay = 2), afterTwo)
        assertTrue((1..3).all { DailyUnlockRules.isUnlocked(it, afterTwo) })
        assertFalse(DailyUnlockRules.isUnlocked(4, afterTwo))
        assertTrue(DailyUnlockRules.isCompleted(2, afterTwo))
        assertFalse(DailyUnlockRules.isCompleted(3, afterTwo))
        assertEquals(3, DailyUnlockRules.nextAvailableDay(afterTwo, 30))
    }

    @Test
    fun `repeating a completed day changes nothing`() {
        val progress = DailyProgress(lastCompletedDay = 5, cycle = 2)

        assertEquals(progress, DailyUnlockRules.afterFinishing(progress, 3))
        assertEquals(progress, DailyUnlockRules.afterFinishing(progress, 5))
    }

    @Test
    fun `a day that isn't the next one never unlocks anything`() {
        val progress = DailyProgress(lastCompletedDay = 2)

        assertEquals(progress, DailyUnlockRules.afterFinishing(progress, 7))
        assertEquals(progress, DailyUnlockRules.afterFinishing(progress, 0))
        assertEquals(progress, DailyUnlockRules.afterFinishing(progress, -1))
        assertEquals(
            DailyProgress(lastCompletedDay = DailyUnlockRules.MAX_DAY - 1),
            DailyUnlockRules.afterFinishing(DailyProgress(lastCompletedDay = DailyUnlockRules.MAX_DAY - 1), DailyUnlockRules.MAX_DAY + 1)
        )
    }

    @Test
    fun `after the last day it stays on the last day and the cycle is complete`() {
        val done = DailyUnlockRules.afterFinishing(DailyProgress(lastCompletedDay = 29), 30)

        assertEquals(30, done.lastCompletedDay)
        assertEquals(30, DailyUnlockRules.nextAvailableDay(done, 30))
        assertTrue(DailyUnlockRules.isCycleComplete(done, 30))
        assertFalse(DailyUnlockRules.isCycleComplete(DailyProgress(lastCompletedDay = 29), 30))
        // The content grew: the new days unlock one by one
        assertFalse(DailyUnlockRules.isCycleComplete(done, 31))
        assertEquals(31, DailyUnlockRules.nextAvailableDay(done, 31))
    }

    @Test
    fun `without routines there is no available day`() {
        assertNull(DailyUnlockRules.nextAvailableDay(DailyProgress(lastCompletedDay = 3), null))
        assertNull(DailyUnlockRules.nextAvailableDay(DailyProgress(), 0))
        assertFalse(DailyUnlockRules.isCycleComplete(DailyProgress(), null))
    }

    @Test
    fun `restarting locks every day again in a new cycle`() {
        val restarted = DailyUnlockRules.restart(DailyProgress(lastCompletedDay = 30, cycle = 1))

        assertEquals(DailyProgress(lastCompletedDay = 0, cycle = 2), restarted)
        assertTrue(DailyUnlockRules.isUnlocked(1, restarted))
        assertFalse(DailyUnlockRules.isUnlocked(2, restarted))
        assertEquals(1, DailyUnlockRules.nextAvailableDay(restarted, 30))
    }

    @Test
    fun `the history gives the highest DAILY day, so nobody loses access`() {
        val history = listOf(
            HistoryEntry(TrainingSource.DAILY, "daily:3"),
            HistoryEntry(TrainingSource.DAILY, "daily:12"),
            HistoryEntry(TrainingSource.DAILY, "daily:7"),
            HistoryEntry(TrainingSource.GYM, "gym:64"),
            HistoryEntry(TrainingSource.PROGRAM, "daily:40"),
            HistoryEntry(TrainingSource.DAILY, null),
            HistoryEntry(TrainingSource.DAILY, "daily:x"),
            HistoryEntry(TrainingSource.DAILY, "daily:-5")
        )

        assertEquals(DailyProgress(lastCompletedDay = 12, cycle = 0), DailyUnlockRules.fromHistory(history))
        assertEquals(DailyProgress(), DailyUnlockRules.fromHistory(emptyList()))
        assertEquals(
            DailyUnlockRules.MAX_DAY,
            DailyUnlockRules.fromHistory(listOf(HistoryEntry(TrainingSource.DAILY, "daily:5000"))).lastCompletedDay
        )
    }

    @Test
    fun `merging keeps the higher cycle, then the further day`() {
        val oldCycleFar = DailyProgress(lastCompletedDay = 20, cycle = 0)
        val newCycleNear = DailyProgress(lastCompletedDay = 1, cycle = 1)

        assertEquals(newCycleNear, DailyUnlockRules.merge(oldCycleFar, newCycleNear))
        assertEquals(newCycleNear, DailyUnlockRules.merge(newCycleNear, oldCycleFar))
        assertEquals(
            DailyProgress(lastCompletedDay = 9, cycle = 3),
            DailyUnlockRules.merge(DailyProgress(lastCompletedDay = 4, cycle = 3), DailyProgress(lastCompletedDay = 9, cycle = 3))
        )
    }
}
