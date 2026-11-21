package com.javargasp.tiominutermvi.domain.daily

import com.javargasp.tiominutermvi.domain.daily.DailyProgressSync.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The daily progress meets the server's: the more advanced state stays, on both sides. */
class DailyProgressSyncTest {

    private val noHistory = DailyProgress()

    @Test
    fun `the same cycle keeps the further day, whichever side has it`() {
        assertEquals(
            Outcome.ApplyRemote(DailyProgress(7, 0)),
            DailyProgressSync.reconcile(local = DailyProgress(3, 0), remote = DailyProgress(7, 0), history = noHistory)
        )
        assertEquals(
            Outcome.PushLocal(DailyProgress(7, 0)),
            DailyProgressSync.reconcile(local = DailyProgress(7, 0), remote = DailyProgress(3, 0), history = noHistory)
        )
    }

    @Test
    fun `a restart (a newer cycle) wins over an older cycle, even further along`() {
        assertEquals(
            Outcome.ApplyRemote(DailyProgress(0, 1)),
            DailyProgressSync.reconcile(local = DailyProgress(25, 0), remote = DailyProgress(0, 1), history = noHistory)
        )
        assertEquals(
            Outcome.PushLocal(DailyProgress(2, 2)),
            DailyProgressSync.reconcile(local = DailyProgress(2, 2), remote = DailyProgress(30, 1), history = noHistory)
        )
    }

    @Test
    fun `equal states write nothing`() {
        assertEquals(Outcome.InSync, DailyProgressSync.reconcile(DailyProgress(4, 1), DailyProgress(4, 1), noHistory))
    }

    @Test
    fun `no server document yet uploads the device's state, raised to its history in cycle 0`() {
        // A fresh install read the progress before the account's entries arrived
        assertEquals(
            Outcome.PushLocal(DailyProgress(12, 0)),
            DailyProgressSync.reconcile(local = DailyProgress(0, 0), remote = null, history = DailyProgress(12, 0))
        )
        // Already further than the history
        assertEquals(
            Outcome.PushLocal(DailyProgress(15, 0)),
            DailyProgressSync.reconcile(local = DailyProgress(15, 0), remote = null, history = DailyProgress(12, 0))
        )
        // A restarted device keeps its restart: its history belongs to an older cycle
        assertEquals(
            Outcome.PushLocal(DailyProgress(1, 1)),
            DailyProgressSync.reconcile(local = DailyProgress(1, 1), remote = null, history = DailyProgress(12, 0))
        )
    }

    @Test
    fun `a guest meeting an account merges both ways`() {
        // The guest got further than the account: the account learns it
        assertEquals(
            Outcome.PushLocal(DailyProgress(9, 0)),
            DailyProgressSync.reconcile(local = DailyProgress(9, 0), remote = DailyProgress(4, 0), history = DailyProgress(9, 0))
        )
        // The account restarted elsewhere: the guest's old cycle gives way
        assertEquals(
            Outcome.ApplyRemote(DailyProgress(2, 1)),
            DailyProgressSync.reconcile(local = DailyProgress(9, 0), remote = DailyProgress(2, 1), history = DailyProgress(9, 0))
        )
    }
}
