package com.javargasp.tiominutermvi.domain.goal

import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry

/** Shortest workout that counts toward the weekly goal: 10 minutes. */
const val MIN_COUNTED_DURATION_SECONDS = 10 * 60

/**
 * The single counting rule: an entry counts toward the weekly goal when it lasted at
 * least [MIN_COUNTED_DURATION_SECONDS] or its duration is unknown. Shorter entries stay
 * in the history but don't count.
 */
fun countsTowardGoal(entry: TrainingLogEntry): Boolean {
    val seconds = entry.durationSeconds ?: return true
    return seconds >= MIN_COUNTED_DURATION_SECONDS
}

/** Counted workouts per local date ("yyyy-MM-dd"); dates without one are absent. */
fun countedWorkoutsPerDay(entries: List<TrainingLogEntry>): Map<String, Int> =
    entries.filter(::countsTowardGoal).groupingBy { it.dateLocal }.eachCount()
