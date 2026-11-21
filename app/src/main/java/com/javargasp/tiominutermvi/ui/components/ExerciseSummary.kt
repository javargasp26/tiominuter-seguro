package com.javargasp.tiominutermvi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise

/**
 * Localized pieces of an exercise summary. Resolved from string resources in
 * the UI ([exerciseSummary] composable); plain lambdas in unit tests.
 */
data class ExerciseSummaryLabels(
    /** "Sets: 4" */
    val sets: (count: Int) -> String,
    /** "Reps: 10, 10, 10" (values already joined) */
    val reps: (values: String) -> String,
    /** "Time: 30s, 45s" (values already joined) */
    val time: (values: String) -> String,
    /** "30s" */
    val seconds: (seconds: Int) -> String,
    /** "Sets: 4 · Reps: 10, 10" */
    val join: (sets: String, values: String) -> String
)

/**
 * One-line summary of a gym exercise slot, e.g. "Sets: 4 · Reps: 10, 10, 10, 10"
 * or "Sets: 2 · Time: 30s, 45s". [reps] holds one value per set (rep count for
 * REPS, seconds for TIME).
 */
fun exerciseSummary(type: ExerciseType, reps: List<Int>, labels: ExerciseSummaryLabels): String {
    val values = when (type) {
        ExerciseType.REPS -> labels.reps(reps.joinToString(VALUE_SEPARATOR))
        ExerciseType.TIME -> labels.time(reps.joinToString(VALUE_SEPARATOR) { labels.seconds(it) })
    }
    return labels.join(labels.sets(reps.size), values)
}

/** [exerciseSummary] with labels from the current locale's string resources. */
@Composable
fun exerciseSummary(exercise: GymRoutineExercise): String {
    // stringResource keeps this in sync with configuration changes; the
    // templates are then formatted with the current locale.
    val locale = LocalConfiguration.current.locales[0]
    val setsTemplate = stringResource(R.string.gym_summary_sets)
    val repsTemplate = stringResource(R.string.gym_summary_reps)
    val timeTemplate = stringResource(R.string.gym_summary_time)
    val secondsTemplate = stringResource(R.string.gym_summary_seconds)
    val joinTemplate = stringResource(R.string.gym_summary)
    val labels = ExerciseSummaryLabels(
        sets = { String.format(locale, setsTemplate, it) },
        reps = { String.format(locale, repsTemplate, it) },
        time = { String.format(locale, timeTemplate, it) },
        seconds = { String.format(locale, secondsTemplate, it) },
        join = { sets, values -> String.format(locale, joinTemplate, sets, values) }
    )
    return exerciseSummary(exercise.type, exercise.reps, labels)
}

private const val VALUE_SEPARATOR = ", "
