package com.javargasp.tiominutermvi.ui.program.circuit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.ExerciseAnimation
import com.javargasp.tiominutermvi.ui.components.REST_ANIMATION
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.program.common.KeepScreenOn
import com.javargasp.tiominutermvi.ui.program.common.ProgramCircularCountdown
import com.javargasp.tiominutermvi.ui.program.common.ProgramConfirmDialog
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramNotFound
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.ProgramWorkoutFinished
import com.javargasp.tiominutermvi.ui.program.common.isSpanish
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.javargasp.tiominutermvi.ui.components.showSessionSaved

/** Home circuit of a program. Keeps the screen on while visible. */
@Composable
fun ProgramCircuitScreen(
    programId: Long,
    week: Int,
    dayIndex: Int,
    onNavigateBack: () -> Unit,
    viewModel: ProgramCircuitViewModel = koinViewModel { parametersOf(programId, week, dayIndex) }
) {
    KeepScreenOn()
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                ProgramCircuitEffect.NavigateBack -> onNavigateBack()
                is ProgramCircuitEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
                is ProgramCircuitEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
            }
        }
    ) { state, onIntent ->
        BackHandler { onIntent(ProgramCircuitIntent.OnBackPressed) }
        ProgramCircuitContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramCircuitContent(
    state: ProgramCircuitState,
    onIntent: (ProgramCircuitIntent) -> Unit
) {
    val onBack = { onIntent(ProgramCircuitIntent.OnBackPressed) }
    Scaffold(
        topBar = {
            ProgramTopBar(
                title = stringResource(R.string.program_title_home_circuit),
                subtitle = stringResource(R.string.program_week_title, state.week),
                onBack = onBack
            )
        }
    ) { padding ->
        val workout = state.workout
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isNotFound || workout == null -> ProgramNotFound(
                text = stringResource(R.string.program_not_found),
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            state.isFinished -> ProgramWorkoutFinished(
                onDone = { onIntent(ProgramCircuitIntent.OnDone) },
                modifier = Modifier.padding(padding),
                photo = state.completionPhoto,
                onPhotoSelected = { onIntent(ProgramCircuitIntent.OnCompletionPhotoSelected(it)) }
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircuitBody(state, onIntent)
            }
        }
    }

    if (state.showExitConfirmation) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_leave_workout_title),
            text = stringResource(R.string.program_leave_workout_text),
            confirmLabel = stringResource(R.string.program_leave),
            dismissLabel = stringResource(R.string.program_stay),
            onConfirm = { onIntent(ProgramCircuitIntent.OnConfirmExit) },
            onDismiss = { onIntent(ProgramCircuitIntent.OnDismissExit) }
        )
    }
}

@Composable
private fun CircuitBody(state: ProgramCircuitState, onIntent: (ProgramCircuitIntent) -> Unit) {
    val workout = state.workout ?: return
    val spanish = isSpanish()
    val unknown = stringResource(R.string.program_unknown_exercise)
    val exercise = state.shownExerciseId?.let { state.exercises[it] }
    val exerciseName = exercise?.displayName(spanish) ?: unknown
    val phase = state.phase

    Text(
        text = stringResource(R.string.program_round_of, state.currentRound, state.totalRounds),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    val (title, animation) = when (phase) {
        is CircuitPhase.Work -> exerciseName to exercise?.animation.orEmpty()
        is CircuitPhase.Rest -> stringResource(R.string.program_rest) to REST_ANIMATION
        is CircuitPhase.RoundRest -> stringResource(R.string.program_round_rest) to REST_ANIMATION
        null -> exerciseName to exercise?.animation.orEmpty()
    }
    Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
        ExerciseAnimation(animation = animation, contentDescription = title, modifier = Modifier.size(200.dp))
    }
    when (phase) {
        null -> {
            Text(
                text = pluralStringResource(R.plurals.program_rounds, workout.rounds, workout.rounds) + " · " +
                    stringResource(R.string.program_work_rest_seconds, workout.workSeconds, workout.restSeconds),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            workout.exerciseIds.forEachIndexed { index, id ->
                Text(
                    text = "${index + 1}. ${state.exercises[id]?.displayName(spanish) ?: unknown}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = { onIntent(ProgramCircuitIntent.OnStart) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) { Text(stringResource(R.string.program_start)) }
        }
        is CircuitPhase.Work -> {
            Text(text = stringResource(R.string.program_work), style = MaterialTheme.typography.titleMedium)
            ProgramCircularCountdown(remainingSeconds = phase.remainingSeconds, totalSeconds = phase.totalSeconds)
            SkipButton(onIntent)
        }
        is CircuitPhase.Rest -> {
            ProgramCircularCountdown(remainingSeconds = phase.remainingSeconds, totalSeconds = phase.totalSeconds)
            Text(text = stringResource(R.string.program_next, exerciseName), style = MaterialTheme.typography.bodyLarge)
            SkipButton(onIntent)
        }
        is CircuitPhase.RoundRest -> {
            ProgramCircularCountdown(remainingSeconds = phase.remainingSeconds, totalSeconds = phase.totalSeconds)
            Text(text = stringResource(R.string.program_next, exerciseName), style = MaterialTheme.typography.bodyLarge)
            SkipButton(onIntent)
        }
    }
}

@Composable
private fun SkipButton(onIntent: (ProgramCircuitIntent) -> Unit) {
    OutlinedButton(
        onClick = { onIntent(ProgramCircuitIntent.OnSkip) },
        modifier = Modifier.heightIn(min = 48.dp)
    ) { Text(stringResource(R.string.program_skip)) }
}
