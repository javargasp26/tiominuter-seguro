package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.domain.session.ActiveSession
import com.javargasp.tiominutermvi.domain.session.ActiveSessionStore
import com.javargasp.tiominutermvi.domain.session.ActiveSessionTracker
import kotlinx.coroutines.yield

/**
 * In-memory [ActiveSessionStore]: [session] is the stored row, [abandoned] what was recorded for
 * inactivity. A transaction that throws is rolled back, like Room; [yieldInTransaction] makes
 * concurrent callers interleave; [recordError] makes recording an abandoned session fail.
 */
class InMemoryActiveSessionStore : ActiveSessionStore {
    var session: ActiveSession? = null
    val abandoned = mutableListOf<ActiveSession>()
    var yieldInTransaction = false
    var recordError: Exception? = null

    /** Every saved state, to check how often the active time was written. */
    val saves = mutableListOf<ActiveSession>()

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val sessionBefore = session
        val abandonedBefore = abandoned.toList()
        return try {
            if (yieldInTransaction) yield()
            block()
        } catch (e: Throwable) {
            session = sessionBefore
            abandoned.clear()
            abandoned.addAll(abandonedBefore)
            throw e
        }
    }

    override suspend fun get(): ActiveSession? = session
    override suspend fun save(session: ActiveSession) {
        this.session = session
        saves += session
    }
    override suspend fun delete() {
        session = null
    }
    override suspend fun recordAbandoned(session: ActiveSession) {
        recordError?.let { throw it }
        abandoned += session
    }
}

/**
 * An [ActiveSessionTracker] over in-memory doubles and a hand-moved wall clock. A ViewModel test
 * that leaves a workout counting must stop it (back, pause or finish) before the end: its
 * periodic save would keep `runTest` draining virtual time forever.
 */
class ActiveSessionTestKit {
    val clock = FakeWallClock()
    val store = InMemoryActiveSessionStore()
    val tracker = ActiveSessionTracker(store, clock, "TestSession")
}
