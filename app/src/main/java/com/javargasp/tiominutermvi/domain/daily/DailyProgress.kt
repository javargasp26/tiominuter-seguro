package com.javargasp.tiominutermvi.domain.daily

import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.dailyRoutineIdOf
import kotlinx.coroutines.flow.Flow

/**
 * How far the person got in the daily routines: every day up to [lastCompletedDay] was finished
 * in this [cycle] (0 = none yet). [cycle] grows by one with every restart ("Restart days").
 * Synced as `users/{uid}/dailyProgress/state` (docs/firestore-model.md).
 */
data class DailyProgress(val lastCompletedDay: Int = 0, val cycle: Int = 0)

/**
 * The unlock rules of the daily routines (pure; ported to the web as `domain/daily_progress.dart`,
 * checked by shared/golden/daily_unlock.json). A day is a routineId (the content pins
 * `routineDay == "Day <routineId>"`).
 *
 * - Day 1 is always available; day N + 1 unlocks when day N is finished (the workout reached its
 *   completion screen). A session closed for inactivity unlocks nothing.
 * - Unlocked days stay available and can be repeated.
 * - Restarting locks every day again from Day 1 and starts a new cycle.
 * - Two states meet (a pull, a sign-in): the higher cycle wins; in the same cycle, the further day.
 */
object DailyUnlockRules {

    /** The highest day the model stores (`lastCompletedDay` 0..999 in the rules). */
    const val MAX_DAY = 999

    /** "Day X available": the next day to do, capped at the last routine; null without routines. */
    fun nextAvailableDay(progress: DailyProgress, lastRoutineId: Int?): Int? {
        if (lastRoutineId == null || lastRoutineId < 1) return null
        return (progress.lastCompletedDay + 1).coerceAtMost(lastRoutineId)
    }

    fun isUnlocked(day: Int, progress: DailyProgress): Boolean = day >= 1 && day <= progress.lastCompletedDay + 1

    fun isCompleted(day: Int, progress: DailyProgress): Boolean = day >= 1 && day <= progress.lastCompletedDay

    /** Every routine was finished in this cycle: "Start again from Day 1" is offered. */
    fun isCycleComplete(progress: DailyProgress, lastRoutineId: Int?): Boolean =
        lastRoutineId != null && lastRoutineId >= 1 && progress.lastCompletedDay >= lastRoutineId

    /**
     * After finishing [day]: the next day unlocks only when [day] is the newest unlocked one. A
     * repeated day, or a day that is no longer unlocked (a restart arrived meanwhile), changes nothing.
     */
    fun afterFinishing(progress: DailyProgress, day: Int): DailyProgress =
        if (day == progress.lastCompletedDay + 1 && day <= MAX_DAY) progress.copy(lastCompletedDay = day) else progress

    /** "Restart days": back to Day 1, in a new cycle (the history is not touched). */
    fun restart(progress: DailyProgress): DailyProgress = DailyProgress(lastCompletedDay = 0, cycle = progress.cycle + 1)

    /**
     * The first state of a person who trained before the unlock existed: the highest day among their
     * DAILY entries (`daily:<N>`), so nobody loses access; 0 without one.
     */
    fun fromHistory(entries: List<HistoryEntry>): DailyProgress {
        val highest = entries
            .filter { it.source == TrainingSource.DAILY }
            .mapNotNull { dailyRoutineIdOf(it.refId) }
            .filter { it >= 1 }
            .maxOrNull() ?: 0
        return DailyProgress(lastCompletedDay = highest.coerceAtMost(MAX_DAY), cycle = 0)
    }

    /** The more advanced of two states: the higher cycle, then the further day. */
    fun merge(a: DailyProgress, b: DailyProgress): DailyProgress = if (compare(a, b) >= 0) a else b

    /** > 0 when [a] is more advanced than [b] (see [merge]). */
    fun compare(a: DailyProgress, b: DailyProgress): Int =
        compareValuesBy(a, b, DailyProgress::cycle, DailyProgress::lastCompletedDay)

    /** The source and refId of a training entry: all [fromHistory] reads. */
    data class HistoryEntry(val source: TrainingSource, val refId: String?)
}

/**
 * The daily progress (local, synced for a signed-in account). The first read of a device with no
 * stored progress initialises it from the history ([DailyUnlockRules.fromHistory]), once.
 */
interface DailyProgressRepository {
    fun observeProgress(): Flow<DailyProgress>

    /** The daily workout of [routineId] was finished (its completion screen opened). */
    suspend fun onDayFinished(routineId: Int)

    /** "Restart days" / "Start again from Day 1". */
    suspend fun restart()
}
