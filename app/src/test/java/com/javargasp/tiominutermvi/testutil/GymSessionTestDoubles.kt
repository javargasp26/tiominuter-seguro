package com.javargasp.tiominutermvi.testutil

import com.javargasp.tiominutermvi.domain.gymsession.GymSession
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionStore
import com.javargasp.tiominutermvi.domain.gymsession.GymSessionTracker
import com.javargasp.tiominutermvi.domain.time.WallClock
import com.javargasp.tiominutermvi.domain.traininglog.SessionEntryRecorder
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.coroutines.yield

/**
 * In-memory [GymSessionStore]. A transaction that throws is rolled back (sessions and progress
 * resets restored), like Room. [yieldInTransaction] suspends inside every transaction so
 * concurrent callers interleave. [onClearProgress] lets a test reset its own progress fake.
 */
class InMemoryGymSessionStore(
    private val titles: Map<Int, String> = emptyMap(),
    private val onClearProgress: (Int) -> Unit = {}
) : GymSessionStore {
    val sessions = mutableMapOf<Int, GymSession>()
    val clearedProgress = mutableListOf<Int>()
    var yieldInTransaction = false

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        val sessionsBefore = sessions.toMap()
        val clearedBefore = clearedProgress.toList()
        return try {
            if (yieldInTransaction) yield()
            block()
        } catch (e: Throwable) {
            sessions.clear()
            sessions.putAll(sessionsBefore)
            clearedProgress.clear()
            clearedProgress.addAll(clearedBefore)
            throw e
        }
    }

    override suspend fun get(routineId: Int): GymSession? = sessions[routineId]
    override suspend fun getAll(): List<GymSession> = sessions.values.sortedBy { it.routineId }
    override suspend fun save(session: GymSession) {
        sessions[session.routineId] = session
    }
    override suspend fun delete(routineId: Int) {
        sessions.remove(routineId)
    }
    override suspend fun clearProgress(routineId: Int) {
        clearedProgress += routineId
        onClearProgress(routineId)
    }
    override suspend fun routineTitle(routineId: Int): String? = titles[routineId] ?: "Routine $routineId"
}

/** [SessionEntryRecorder] that keeps what it records; [error] makes it throw. */
class RecordingSessionEntryRecorder : SessionEntryRecorder {
    data class Entry(
        val source: TrainingSource,
        val refId: String?,
        val title: String,
        val durationSeconds: Int,
        val completedAt: Long,
        val dateOfMillis: Long
    )

    val entries = mutableListOf<Entry>()
    var nextEntryId = 1L
    var error: Exception? = null

    override suspend fun recordSession(
        source: TrainingSource,
        refId: String?,
        title: String,
        durationSeconds: Int,
        completedAt: Long,
        dateOfMillis: Long
    ): Long {
        error?.let { throw it }
        entries += Entry(source, refId, title, durationSeconds, completedAt, dateOfMillis)
        return nextEntryId++
    }
}

/** A clock a test moves by hand (wall-clock millis). */
class FakeWallClock(var now: Long = 0L) : WallClock {
    override fun nowMillis(): Long = now
}

/** A [GymSessionTracker] over in-memory doubles, for ViewModel tests. */
class GymSessionTestKit(titles: Map<Int, String> = emptyMap(), onClearProgress: (Int) -> Unit = {}) {
    val clock = FakeWallClock()
    val store = InMemoryGymSessionStore(titles, onClearProgress)
    val recorder = RecordingSessionEntryRecorder()
    val tracker = GymSessionTracker(store, recorder, clock)
}
