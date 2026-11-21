package com.javargasp.tiominutermvi.ui.program.common

import com.javargasp.tiominutermvi.ui.components.share.ShareInGroupsSection
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegmentLabel
import com.javargasp.tiominutermvi.domain.program.model.titleCode
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.components.TimerText
import com.javargasp.tiominutermvi.ui.components.programTitleRes
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoSection
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import java.util.Locale

// ---------------------------------------------------------------------------
// Locale
// ---------------------------------------------------------------------------

/** The locale of the current configuration (follows language changes). */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Exercise names use `nameEs` when the app runs in Spanish. */
@Composable
fun isSpanish(): Boolean = currentLocale().language == "es"

// ---------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------

@Composable
fun goalLabel(goal: ProgramGoal): String = stringResource(
    when (goal) {
        ProgramGoal.GAIN_MUSCLE -> R.string.program_goal_gain_muscle
        ProgramGoal.LOSE_FAT -> R.string.program_goal_lose_fat
    }
)

@Composable
fun focusLabel(focus: ProgramFocus): String = stringResource(
    when (focus) {
        ProgramFocus.BALANCED -> R.string.program_focus_balanced
        ProgramFocus.LOWER_BODY -> R.string.program_focus_lower_body
        ProgramFocus.UPPER_BODY -> R.string.program_focus_upper_body
        ProgramFocus.CORE -> R.string.program_focus_core
    }
)

/** One line under the focus label: "More glutes and legs". */
@Composable
fun focusDescription(focus: ProgramFocus): String = stringResource(
    when (focus) {
        ProgramFocus.BALANCED -> R.string.program_focus_balanced_description
        ProgramFocus.LOWER_BODY -> R.string.program_focus_lower_body_description
        ProgramFocus.UPPER_BODY -> R.string.program_focus_upper_body_description
        ProgramFocus.CORE -> R.string.program_focus_core_description
    }
)

@Composable
fun levelLabel(level: ProgramLevel): String = stringResource(
    when (level) {
        ProgramLevel.BEGINNER -> R.string.program_level_beginner
        ProgramLevel.INTERMEDIATE -> R.string.program_level_intermediate
        ProgramLevel.ADVANCED -> R.string.program_level_advanced
    }
)

@Composable
fun locationLabel(location: ProgramLocation): String = stringResource(
    when (location) {
        ProgramLocation.HOME -> R.string.program_location_home
        ProgramLocation.GYM -> R.string.program_location_gym
    }
)

/** "6 weeks · 3 days per week". */
@Composable
fun durationLabel(profile: ProgramProfile): String = stringResource(
    R.string.program_duration,
    pluralStringResource(R.plurals.program_weeks, profile.weeks, profile.weeks),
    pluralStringResource(R.plurals.program_days_per_week, profile.daysPerWeek, profile.daysPerWeek)
)

/** "Full body A", "Upper B", "Treadmill", "Outdoor run", "Brisk walk", "Home circuit"... */
@Composable
fun workoutTitle(workout: ProgramWorkout): String = stringResource(workoutTitleRes(workout))

/**
 * String resource of [workoutTitle]; also used for the training history title. The one table of
 * program titles is the core's `programTitleRes` (the history and the feed resolve the same code).
 */
@StringRes
fun workoutTitleRes(workout: ProgramWorkout): Int = programTitleRes(workout.titleCode())

@Composable
fun treadmillSegmentLabel(label: TreadmillSegmentLabel): String = stringResource(
    when (label) {
        TreadmillSegmentLabel.WARM_UP -> R.string.program_segment_warm_up
        TreadmillSegmentLabel.JOG -> R.string.program_segment_jog
        TreadmillSegmentLabel.WALK -> R.string.program_segment_walk
        TreadmillSegmentLabel.INCLINE_WALK -> R.string.program_segment_incline_walk
        TreadmillSegmentLabel.COOL_DOWN -> R.string.program_segment_cool_down
    }
)

/**
 * Short summary of a workout for cards and the preview: "6 exercises",
 * "3.0 km", "35 min · 6 intervals", "3 rounds · 5 exercises".
 */
@Composable
fun workoutSummary(workout: ProgramWorkout): String = when (workout) {
    is ProgramWorkout.Strength ->
        pluralStringResource(R.plurals.program_exercises_count, workout.slots.size, workout.slots.size)
    is ProgramWorkout.OutdoorRun ->
        stringResource(R.string.program_distance_km, formatOneDecimal(workout.distanceKm, currentLocale()))
    is ProgramWorkout.Treadmill -> {
        val minutes = (workout.segments.sumOf { it.durationSeconds } + 59) / 60
        val intervals = workout.segments.count {
            it.label == TreadmillSegmentLabel.JOG || it.label == TreadmillSegmentLabel.INCLINE_WALK
        }
        stringResource(
            R.string.program_treadmill_summary,
            pluralStringResource(R.plurals.program_minutes, minutes, minutes),
            pluralStringResource(R.plurals.program_intervals, intervals, intervals)
        )
    }
    is ProgramWorkout.HomeCircuit -> stringResource(
        R.string.program_circuit_summary,
        pluralStringResource(R.plurals.program_rounds, workout.rounds, workout.rounds),
        pluralStringResource(R.plurals.program_exercises_count, workout.exerciseIds.size, workout.exerciseIds.size)
    )
}

/** "Sets: 4 · Reps: 10, 10, 10, 10" or "Sets: 2 · Time: 30s, 45s" (+ " per side") for one strength slot. */
@Composable
fun programExerciseSummary(slot: StrengthSlot): String {
    val secondsFormat = stringResource(R.string.program_seconds_short)
    return programExerciseSummary(
        isTime = slot.type == ProgramSlotType.TIME,
        values = slot.reps,
        labels = ProgramSummaryLabels(
            sets = stringResource(R.string.program_summary_sets),
            reps = stringResource(R.string.program_summary_reps),
            time = stringResource(R.string.program_summary_time),
            perSide = stringResource(R.string.program_per_side),
            seconds = { String.format(Locale.ROOT, secondsFormat, it) }
        ),
        perSide = slot.perSide
    )
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/** Keeps the screen on while the calling composable is shown. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramTopBar(title: String, onBack: () -> Unit, subtitle: String? = null) {
    TopAppBar(
        title = {
            Column {
                Text(text = title)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.program_back)
                )
            }
        }
    )
}

@Composable
fun ProgramConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

@Composable
fun ProgramLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Shown when a program or workout no longer exists. */
@Composable
fun ProgramNotFound(text: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onBack) { Text(stringResource(R.string.program_back_to_program)) }
    }
}

/** A circular countdown: ring for [remainingSeconds] of [totalSeconds] with the time inside. */
@Composable
fun ProgramCircularCountdown(remainingSeconds: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
    Box(modifier = modifier.size(180.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(180.dp),
            strokeWidth = 10.dp
        )
        TimerText(seconds = remainingSeconds, style = MaterialTheme.typography.displaySmall)
    }
}

/**
 * End of a cardio workout: "Workout completed!", an optional photo of this completion
 * ([photo], hidden until its training-history entry is known) and a button back to the program.
 */
@Composable
fun ProgramWorkoutFinished(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    photo: CompletionPhotoState = CompletionPhotoState(),
    onPhotoSelected: (TrainingPhotoSource) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.program_workout_completed_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.program_workout_completed_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        CompletionPhotoSection(photo = photo, onPhotoSelected = onPhotoSelected)
        ShareInGroupsSection(entryId = photo.entryId)
        Button(onClick = onDone) { Text(stringResource(R.string.program_back_to_program)) }
    }
}
