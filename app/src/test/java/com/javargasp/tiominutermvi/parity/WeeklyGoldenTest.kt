package com.javargasp.tiominutermvi.parity

import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgressCalculator
import com.javargasp.tiominutermvi.domain.goal.countedWorkoutsPerDay
import com.javargasp.tiominutermvi.domain.goal.countsTowardGoal
import com.javargasp.tiominutermvi.domain.groups.GroupSummaryCalculator
import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import kotlinx.serialization.json.JsonElement
import org.junit.Test
import java.util.Random

/**
 * shared/golden/weekly.json: the 10-minute counting rule, the weekly goal in force, the week's
 * progress, the streak (WeeklyProgressCalculator) and the group member summary
 * (GroupSummaryCalculator) over hand-written edge cases (9:59 vs 10:00, null durations, Monday and
 * Sunday, a new year, a leap day, stale weeks, goal changes, a future-dated entry, the caps) and
 * generated ones. Generated cases come from java.util.Random with the seed written in the case,
 * and their entries are written out too, so the Dart side reads the inputs as they are.
 */
class WeeklyGoldenTest {

    private class Case(val id: String, val today: String, val goals: List<WeeklyGoal>, val entries: List<Entry>, val seed: Long? = null)

    private class Entry(val dateLocal: String, val source: TrainingSource, val durationSeconds: Int?)

    private fun goal(workouts: Int, week: String) = WeeklyGoal(workouts, week)

    private fun e(date: String, duration: Int?, source: TrainingSource = TrainingSource.DAILY) = Entry(date, source, duration)

    private val handWritten = listOf(
        Case(
            "duration boundaries: 0, 1, 599, 600, 601, null",
            "2026-10-21",
            listOf(goal(3, "2026-10-19")),
            listOf(e("2026-10-19", 0), e("2026-10-19", 1), e("2026-10-20", 599), e("2026-10-20", 600), e("2026-10-21", 601), e("2026-10-21", null))
        ),
        Case(
            "today is Monday: Sunday belongs to last week",
            "2026-10-19",
            listOf(goal(1, "2026-10-05")),
            listOf(e("2026-10-18", 900), e("2026-10-19", 900), e("2026-10-11", 1200))
        ),
        Case(
            "today is Sunday: the whole week counts",
            "2026-10-25",
            listOf(goal(2, "2026-10-19")),
            listOf(e("2026-10-19", 600, TrainingSource.GYM), e("2026-10-25", 600, TrainingSource.PROGRAM), e("2026-10-26", 600))
        ),
        Case("no goal: no streak, no summary", "2026-10-21", emptyList(), listOf(e("2026-10-20", 1200))),
        Case("a goal that starts next week only", "2026-10-21", listOf(goal(2, "2026-10-26")), listOf(e("2026-10-21", 1200))),
        Case(
            "goal changes: the goal in force each week decides the streak",
            "2026-10-21",
            listOf(goal(3, "2026-09-21"), goal(1, "2026-10-05"), goal(4, "2026-10-19")),
            listOf(
                e("2026-09-21", 700), e("2026-09-22", 700), e("2026-09-23", 700),
                e("2026-09-29", 700), e("2026-09-30", 700), e("2026-10-01", 700),
                e("2026-10-06", 700),
                e("2026-10-12", 700),
                e("2026-10-19", 700), e("2026-10-20", 700)
            )
        ),
        Case(
            "a missed week breaks the streak; the current week never does",
            "2026-10-21",
            listOf(goal(1, "2026-09-07")),
            listOf(e("2026-09-08", 700), e("2026-09-15", 700), e("2026-09-29", 700), e("2026-10-06", 700), e("2026-10-13", 700))
        ),
        Case(
            "the current week met adds one",
            "2026-10-21",
            listOf(goal(1, "2026-09-07")),
            listOf(e("2026-10-06", 700), e("2026-10-13", 700), e("2026-10-20", 700))
        ),
        Case(
            "short workouts don't make a streak",
            "2026-10-21",
            listOf(goal(1, "2026-10-05")),
            listOf(e("2026-10-06", 599), e("2026-10-13", 599), e("2026-10-20", 599))
        ),
        Case(
            "all four sources on one day, repeated: distinct, in source order",
            "2026-10-22",
            listOf(goal(5, "2026-10-19")),
            listOf(
                e("2026-10-22", 900, TrainingSource.ACTIVITY), e("2026-10-22", 900, TrainingSource.PROGRAM),
                e("2026-10-22", 900, TrainingSource.GYM), e("2026-10-22", 900, TrainingSource.DAILY),
                e("2026-10-22", 900, TrainingSource.GYM), e("2026-10-22", 300, TrainingSource.ACTIVITY),
                e("2026-10-20", 300, TrainingSource.PROGRAM)
            )
        ),
        Case(
            "stale history: last trained weeks ago",
            "2026-10-21",
            listOf(goal(2, "2026-01-05")),
            listOf(e("2026-08-03", 900), e("2026-08-04", 900), e("2026-08-10", 300))
        ),
        Case(
            "a future-dated entry (wrong clock) counts in its week but is never 'last trained'",
            "2026-10-21",
            listOf(goal(2, "2026-10-19")),
            listOf(e("2026-10-20", 900), e("2026-10-24", 900))
        ),
        Case(
            "a new year: the week of Thursday 2027-01-01 starts on Monday 2026-12-28",
            "2027-01-01",
            listOf(goal(2, "2026-12-14")),
            listOf(e("2026-12-28", 900), e("2026-12-31", 900), e("2027-01-01", 900), e("2026-12-21", 900), e("2026-12-22", 900))
        ),
        Case(
            "a leap day",
            "2028-02-29",
            listOf(goal(1, "2028-02-14")),
            listOf(e("2028-02-28", 900), e("2028-02-29", 900), e("2028-02-21", 900))
        ),
        Case(
            "more than 100 workouts this week: the summary caps at 100",
            "2026-10-21",
            listOf(goal(14, "2026-10-19")),
            List(105) { e("2026-10-20", 600) }
        ),
        Case(
            "a streak over 1000 weeks: the summary caps at 1000",
            "2026-10-21",
            listOf(goal(1, CalendarDates.plusWeeks("2026-10-21", -1005))),
            List(1006) { e(CalendarDates.plusWeeks("2026-10-21", -it), 600) }
        )
    )

    private val sources = TrainingSource.entries
    private val durations = listOf(null, 0, 59, 300, 599, 600, 601, 900, 1800, 3600)

    private fun generated(index: Int): Case {
        val seed = 20_261_021L + index
        val random = Random(seed)
        val today = CalendarDates.plusDays("2026-01-01", random.nextInt(730))
        val goals = List(random.nextInt(4)) {
            goal(1 + random.nextInt(14), CalendarDates.plusWeeks(today, -random.nextInt(12)))
        }.distinctBy { it.effectiveFromWeek }
        val entries = List(random.nextInt(40)) {
            Entry(
                CalendarDates.plusDays(today, -random.nextInt(84) + if (random.nextInt(20) == 0) 3 else 0),
                sources[random.nextInt(sources.size)],
                durations[random.nextInt(durations.size)]
            )
        }
        return Case("generated #$index", today, goals, entries, seed)
    }

    @Test
    fun `weekly golden`() {
        val cases = handWritten + List(60) { generated(it) }
        Golden.verify(
            "weekly",
            Golden.document(
                domain = "weekly",
                generatedBy = "app/src/test/java/com/javargasp/tiominutermvi/parity/WeeklyGoldenTest.kt",
                description = "countsTowardGoal, countedWorkoutsPerDay, WeeklyProgressCalculator (goalForWeek, progress, " +
                    "streak, currentWeek) and GroupSummaryCalculator.summary",
                cases = cases.map(::case)
            )
        )
    }

    private fun case(case: Case): JsonElement {
        val entries = case.entries.mapIndexed { index, entry ->
            TrainingLogEntry(
                id = index + 1L, uuid = "entry-$index", dateLocal = entry.dateLocal, completedAt = 0L,
                source = entry.source, refId = null, title = "", durationSeconds = entry.durationSeconds,
                createdAt = 0L, updatedAt = 0L
            )
        }
        val perDay = countedWorkoutsPerDay(entries)
        val week = WeeklyProgressCalculator.currentWeek(case.today, case.goals, perDay)
        val summary = GroupSummaryCalculator.summary(case.today, entries, case.goals)
        return obj(
            "id" to str(case.id),
            "input" to obj(
                "seed" to num(case.seed),
                "today" to str(case.today),
                "goals" to arr(case.goals.map { obj("workoutsPerWeek" to num(it.workoutsPerWeek), "effectiveFromWeek" to str(it.effectiveFromWeek)) }),
                "entries" to arr(
                    case.entries.map {
                        obj("dateLocal" to str(it.dateLocal), "source" to str(it.source.name), "durationSeconds" to num(it.durationSeconds))
                    }
                )
            ),
            "output" to obj(
                "counts" to arr(entries.map { bool(countsTowardGoal(it)) }),
                "countedPerDay" to obj(*perDay.toSortedMap().map { (date, count) -> date to num(count) }.toTypedArray()),
                "goalForWeek" to num(WeeklyProgressCalculator.goalForWeek(case.goals, CalendarDates.mondayOf(case.today))),
                "progress" to obj(
                    "weekStart" to str(week.progress.weekStart),
                    "goal" to num(week.progress.goal),
                    "workoutCount" to num(week.progress.workoutCount),
                    "workoutsPerDay" to obj(*week.progress.workoutsPerDay.toSortedMap().map { (d, c) -> d to num(c) }.toTypedArray()),
                    "trainedDates" to arr(week.progress.trainedDates.sorted().map(::str)),
                    "isGoalMet" to bool(week.progress.isGoalMet)
                ),
                "streak" to num(week.streak),
                "workoutsNeeded" to num(week.workoutsNeeded),
                "summary" to (summary?.let {
                    obj(
                        "weekStartLocal" to str(it.weekStartLocal),
                        "workoutsThisWeek" to num(it.workoutsThisWeek),
                        "weeklyGoal" to num(it.weeklyGoal),
                        "streakWeeks" to num(it.streakWeeks),
                        "daysThisWeek" to obj(*it.daysThisWeek.toSortedMap().map { (day, list) -> "$day" to arr(list.map { s -> str(s.name) }) }.toTypedArray()),
                        "lastTrainedLocal" to str(it.lastTrainedLocal)
                    )
                } ?: str(null))
            )
        )
    }
}
