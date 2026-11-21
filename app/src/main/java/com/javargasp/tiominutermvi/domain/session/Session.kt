package com.javargasp.tiominutermvi.domain.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A workout session (gym, program, daily) closes on its own after this long (milliseconds)
 * without activity: 2 hours. The single definition of the timeout.
 */
const val SESSION_INACTIVITY_TIMEOUT = 2L * 60 * 60 * 1000

/** Pure rules shared by every session. */
object SessionRules {

    /**
     * True when more than [SESSION_INACTIVITY_TIMEOUT] passed since [lastActivityAt]. Never when
     * the clock moved back ([now] before [lastActivityAt]): the session stays open.
     */
    fun isInactive(lastActivityAt: Long, now: Long): Boolean =
        now >= lastActivityAt && now - lastActivityAt > SESSION_INACTIVITY_TIMEOUT

    /** [startedAt] → [endedAt] in whole seconds, never negative (a clock moved back clamps at 0). */
    fun durationSeconds(startedAt: Long, endedAt: Long): Int =
        ((endedAt - startedAt).coerceAtLeast(0) / MILLIS_PER_SECOND).toInt()

    /**
     * The latest run of [times] with no gap over the timeout, as (first, last); null without
     * times. Used to derive a session from progress stored before sessions existed, so an
     * exercise done days earlier doesn't stretch it.
     */
    fun latestRun(times: List<Long>): Pair<Long, Long>? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        var start = sorted.last()
        for (index in sorted.lastIndex - 1 downTo 0) {
            if (start - sorted[index] > SESSION_INACTIVITY_TIMEOUT) break
            start = sorted[index]
        }
        return start to sorted.last()
    }
}

/** A transaction of the database a session lives in: everything in [block] happens, or none. */
interface SessionTransactions {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/** Something whose sessions can be closed for inactivity (the app-start check runs every one). */
fun interface InactiveSessionCloser {
    suspend fun closeInactive()
}

/** A session the app closed for inactivity, with the duration it recorded. */
data class ClosedSession(val durationSeconds: Int)

/**
 * The lock of one session kind: every operation holds one [Mutex], runs in one transaction and
 * is not cancelled with its caller (a ViewModel cleared right after a set). So a close is
 * idempotent: two checks at once, or one after the other, write a single entry. A failure rolls
 * the transaction back, is logged (println, as android.util.Log is not available in JVM tests)
 * and returns [default], so a session never breaks a workout.
 */
class SessionLock(private val name: String) {
    private val mutex = Mutex()

    suspend fun <T> run(transactions: SessionTransactions, action: String, default: T, block: suspend () -> T): T =
        try {
            withContext(NonCancellable) { mutex.withLock { transactions.inTransaction(block) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("$name: could not $action: ${e.message}")
            default
        }
}

/** Sessions closed for inactivity and not announced yet; each one is taken only once. */
class ClosedSessionNotices<T> {
    private val pending = MutableStateFlow<List<T>>(emptyList())

    fun add(closed: T) {
        pending.update { it + closed }
    }

    fun take(): List<T> = pending.getAndUpdate { emptyList() }
}

private const val MILLIS_PER_SECOND = 1000L
