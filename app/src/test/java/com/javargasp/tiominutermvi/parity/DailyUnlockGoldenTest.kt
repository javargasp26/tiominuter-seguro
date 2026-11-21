package com.javargasp.tiominutermvi.parity

import com.javargasp.tiominutermvi.domain.daily.DailyProgress
import com.javargasp.tiominutermvi.domain.daily.DailyProgressSync
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules
import com.javargasp.tiominutermvi.domain.daily.DailyUnlockRules.HistoryEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.Test
import java.util.Random

/**
 * shared/golden/daily_unlock.json: the daily unlock rules (DailyUnlockRules) and the sync's merge
 * (DailyProgressSync) over hand-written edge cases (a new person, the last day, a grown or empty
 * catalog, repeats, days out of order, restarts, odd refIds in the history, same and newer cycles,
 * no server document) and generated ones (java.util.Random, the seed written in the case).
 */
class DailyUnlockGoldenTest {

    private fun p(day: Int, cycle: Int = 0) = DailyProgress(day, cycle)

    private fun progressJson(progress: DailyProgress?): JsonElement =
        progress?.let { obj("lastCompletedDay" to num(it.lastCompletedDay), "cycle" to num(it.cycle)) } ?: JsonNull

    /** Unlock state, available day and the effect of finishing each day and of a restart. */
    private fun stateCase(id: String, progress: DailyProgress, lastRoutineId: Int?): JsonElement {
        val top = maxOf(lastRoutineId ?: 0, progress.lastCompletedDay) + 2
        val days = (-1..top).toList()
        return obj(
            "id" to str(id),
            "kind" to str("state"),
            "input" to obj("progress" to progressJson(progress), "lastRoutineId" to num(lastRoutineId), "days" to arr(days.map { num(it) })),
            "output" to obj(
                "nextAvailableDay" to num(DailyUnlockRules.nextAvailableDay(progress, lastRoutineId)),
                "isCycleComplete" to bool(DailyUnlockRules.isCycleComplete(progress, lastRoutineId)),
                "unlocked" to arr(days.map { bool(DailyUnlockRules.isUnlocked(it, progress)) }),
                "completed" to arr(days.map { bool(DailyUnlockRules.isCompleted(it, progress)) }),
                "afterFinishing" to arr(days.map { progressJson(DailyUnlockRules.afterFinishing(progress, it)) }),
                "restart" to progressJson(DailyUnlockRules.restart(progress))
            )
        )
    }

    private fun historyCase(id: String, entries: List<HistoryEntry>): JsonElement = obj(
        "id" to str(id),
        "kind" to str("history"),
        "input" to obj(
            "entries" to arr(entries.map { obj("source" to str(it.source.name), "refId" to str(it.refId)) })
        ),
        "output" to obj("progress" to progressJson(DailyUnlockRules.fromHistory(entries)))
    )

    private fun mergeCase(id: String, local: DailyProgress, remote: DailyProgress?, history: DailyProgress): JsonElement {
        val outcome = when (val result = DailyProgressSync.reconcile(local, remote, history)) {
            is DailyProgressSync.Outcome.ApplyRemote -> obj("action" to str("applyRemote"), "progress" to progressJson(result.progress))
            is DailyProgressSync.Outcome.PushLocal -> obj("action" to str("pushLocal"), "progress" to progressJson(result.progress))
            DailyProgressSync.Outcome.InSync -> obj("action" to str("inSync"), "progress" to JsonNull)
        }
        return obj(
            "id" to str(id),
            "kind" to str("merge"),
            "input" to obj("local" to progressJson(local), "remote" to progressJson(remote), "history" to progressJson(history)),
            "output" to obj(
                "merge" to (remote?.let { progressJson(DailyUnlockRules.merge(local, it)) } ?: JsonNull),
                "compare" to (remote?.let { num(DailyUnlockRules.compare(local, it).coerceIn(-1, 1)) } ?: JsonNull),
                "reconcile" to outcome
            )
        )
    }

    private fun daily(refId: String?) = HistoryEntry(TrainingSource.DAILY, refId)

    @Test
    fun `daily unlock golden`() {
        val cases = mutableListOf<JsonElement>()
        cases += stateCase("a new person: only Day 1", p(0), 30)
        cases += stateCase("in progress: day 6 done", p(6), 30)
        cases += stateCase("the day before the last", p(29), 30)
        cases += stateCase("every day done: stays on the last", p(30), 30)
        cases += stateCase("the catalog grew after the last day", p(30, 2), 32)
        cases += stateCase("the catalog shrank under the progress", p(12), 5)
        cases += stateCase("no routines", p(3), null)
        cases += stateCase("a zero last routine", p(0), 0)
        cases += stateCase("a later cycle", p(2, 7), 30)
        cases += stateCase("at the model's limit", p(DailyUnlockRules.MAX_DAY - 1), DailyUnlockRules.MAX_DAY)
        cases += stateCase("at the model's maximum", p(DailyUnlockRules.MAX_DAY), DailyUnlockRules.MAX_DAY)

        cases += historyCase("no history", emptyList())
        cases += historyCase(
            "the highest DAILY day wins",
            listOf(daily("daily:3"), daily("daily:12"), daily("daily:7"))
        )
        cases += historyCase(
            "other sources and refIds are ignored",
            listOf(
                HistoryEntry(TrainingSource.GYM, "gym:64"),
                HistoryEntry(TrainingSource.PROGRAM, "daily:40"),
                HistoryEntry(TrainingSource.ACTIVITY, null),
                daily("daily:2")
            )
        )
        cases += historyCase(
            "odd refIds (Kotlin's toIntOrNull decides)",
            listOf(
                daily(null), daily(""), daily("daily:"), daily("daily:x"), daily("Daily:9"), daily("daily: 8"),
                daily("daily:-4"), daily("daily:0"), daily("daily:+5"), daily("daily:05"), daily("daily:2147483648"),
                daily("daily:3.0"), daily("gym:64")
            )
        )
        cases += historyCase("capped at the model's maximum", listOf(daily("daily:5000"), daily("daily:20")))

        cases += mergeCase("same cycle, the server further", p(3), p(7), p(0))
        cases += mergeCase("same cycle, the device further", p(7), p(3), p(0))
        cases += mergeCase("equal", p(4, 1), p(4, 1), p(0))
        cases += mergeCase("a restart elsewhere wins over a further old cycle", p(25), p(0, 1), p(25))
        cases += mergeCase("a restart here wins over a further old cycle", p(2, 2), p(30, 1), p(0))
        cases += mergeCase("no server document: the history raises cycle 0", p(0), null, p(12))
        cases += mergeCase("no server document: already further than the history", p(15), null, p(12))
        cases += mergeCase("no server document: a restarted device keeps its restart", p(1, 1), null, p(12))
        cases += mergeCase("a guest further than the account", p(9), p(4), p(9))
        cases += mergeCase("a guest meeting an account restarted elsewhere", p(9), p(2, 1), p(9))

        val seed = 20_261_114L
        val random = Random(seed)
        repeat(40) { index ->
            val local = p(random.nextInt(32), random.nextInt(4))
            val remote = if (random.nextInt(5) == 0) null else p(random.nextInt(32), random.nextInt(4))
            val history = p(random.nextInt(32))
            cases += mergeCase("generated merge $index (seed $seed)", local, remote, history)
        }
        repeat(20) { index ->
            cases += stateCase("generated state $index (seed $seed)", p(random.nextInt(35), random.nextInt(3)), random.nextInt(33))
        }

        Golden.verify(
            "daily_unlock",
            Golden.document(
                domain = "daily_unlock",
                generatedBy = "app/src/test/java/com/javargasp/tiominutermvi/parity/DailyUnlockGoldenTest.kt",
                description = "Daily routines unlock one by one (DailyUnlockRules: the available day, unlocked / completed, " +
                    "finishing a day, restart, the first state from the history) and the sync's merge (DailyProgressSync). " +
                    "compare is clamped to -1 / 0 / 1.",
                cases = cases
            )
        )
    }
}
