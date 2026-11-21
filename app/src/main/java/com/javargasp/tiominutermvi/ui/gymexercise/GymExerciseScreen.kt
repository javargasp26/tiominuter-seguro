package com.javargasp.tiominutermvi.ui.gymexercise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.ExerciseAnimation
import com.javargasp.tiominutermvi.ui.components.KeepScreenOn
import com.javargasp.tiominutermvi.ui.components.REST_ANIMATION
import com.javargasp.tiominutermvi.ui.components.TimerText
import com.javargasp.tiominutermvi.ui.components.localizedName
import com.javargasp.tiominutermvi.ui.components.text
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Gym Exercise screen: guides the user through every set of one exercise slot
 * ([position]) of a gym routine. Keeps the screen on while visible.
 */
@Composable
fun GymExerciseScreen(
    routineId: Int,
    position: Int,
    onNavigateBack: () -> Unit,
    viewModel: GymExerciseViewModel = koinViewModel { parametersOf(routineId, position) }
) {
    KeepScreenOn()

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GymExerciseEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->

        LaunchedEffect(Unit) {
            onIntent(GymExerciseIntent.OnLoad)
        }

        BackHandler {
            onIntent(GymExerciseIntent.OnBackPressed)
        }

        GymExerciseContent(
            state = state,
            onSetDone = { onIntent(GymExerciseIntent.OnSetDone) },
            onStartTimedSet = { onIntent(GymExerciseIntent.OnStartTimedSet) },
            onSkipRest = { onIntent(GymExerciseIntent.OnSkipRest) },
            onBackPressed = { onIntent(GymExerciseIntent.OnBackPressed) },
            onConfirmExit = { onIntent(GymExerciseIntent.OnConfirmExit) },
            onDismissExit = { onIntent(GymExerciseIntent.OnDismissExit) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GymExerciseContent(
    state: GymExerciseState,
    onSetDone: () -> Unit,
    onStartTimedSet: () -> Unit,
    onSkipRest: () -> Unit,
    onBackPressed: () -> Unit,
    onConfirmExit: () -> Unit,
    onDismissExit: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = WorkoutNameResolver.gymName(state.routineName, state.routineNameEs).text()) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val phase = state.phase
        when {
            state.isLoading -> LoadingContent(Modifier.padding(paddingValues))
            state.isNotFound || phase == null -> NotFoundContent(
                onBackPressed = onBackPressed,
                modifier = Modifier.padding(paddingValues)
            )
            else -> ExerciseContent(
                state = state,
                phase = phase,
                onSetDone = onSetDone,
                onStartTimedSet = onStartTimedSet,
                onSkipRest = onSkipRest,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }

    if (state.showExitConfirmation) {
        ExitConfirmationDialog(
            completedSets = state.completedSets,
            totalSets = state.totalSets,
            onConfirmExit = onConfirmExit,
            onDismissExit = onDismissExit
        )
    }
}

@Composable
private fun ExerciseContent(
    state: GymExerciseState,
    phase: GymExercisePhase,
    onSetDone: () -> Unit,
    onStartTimedSet: () -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exerciseName = state.exercise?.exercise?.localizedName() ?: stringResource(R.string.unknown_exercise)
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.exercise_progress, state.exerciseNumber, state.totalExercises),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = exerciseName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val animation = if (phase is GymExercisePhase.Resting) {
                REST_ANIMATION
            } else {
                state.exercise?.exercise?.animation
            }
            if (animation != null) {
                ExerciseAnimation(
                    animation = animation,
                    contentDescription = if (phase is GymExercisePhase.Resting) {
                        stringResource(R.string.gym_exercise_rest_animation_description)
                    } else {
                        stringResource(R.string.exercise_animation_description, exerciseName)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        PhaseContent(state = state, phase = phase)
        Spacer(Modifier.height(16.dp))

        // Fixed height so the layout does not jump when a phase has no action.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            when (phase) {
                is GymExercisePhase.PerformingReps -> PrimaryButton(
                    text = stringResource(R.string.gym_exercise_set_done),
                    onClick = onSetDone
                )
                is GymExercisePhase.ReadyToStartTimed -> PrimaryButton(
                    text = stringResource(R.string.gym_exercise_start),
                    onClick = onStartTimedSet
                )
                is GymExercisePhase.Resting -> PrimaryButton(
                    text = stringResource(R.string.gym_exercise_skip_rest),
                    onClick = onSkipRest
                )
                is GymExercisePhase.GetReady,
                is GymExercisePhase.PerformingTimed -> Unit
            }
        }
    }
}

@Composable
private fun PhaseContent(
    state: GymExerciseState,
    phase: GymExercisePhase
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (phase) {
            is GymExercisePhase.PerformingReps -> {
                SetLabel(setIndex = phase.setIndex, totalSets = state.totalSets)
                val reps = state.exercise?.reps?.getOrNull(phase.setIndex) ?: 0
                Text(
                    text = pluralStringResource(R.plurals.gym_exercise_reps, reps, reps),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is GymExercisePhase.ReadyToStartTimed -> {
                SetLabel(setIndex = phase.setIndex, totalSets = state.totalSets)
                Text(
                    text = stringResource(R.string.gym_exercise_duration),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TimerText(
                    seconds = state.exercise?.reps?.getOrNull(phase.setIndex) ?: 0,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is GymExercisePhase.GetReady -> {
                SetLabel(setIndex = phase.setIndex, totalSets = state.totalSets)
                Text(
                    text = stringResource(R.string.gym_exercise_get_ready),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = phase.remainingSeconds.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            is GymExercisePhase.PerformingTimed -> {
                SetLabel(setIndex = phase.setIndex, totalSets = state.totalSets)
                TimerText(
                    seconds = phase.remainingSeconds,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { progressOf(phase.remainingSeconds, phase.totalSeconds) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is GymExercisePhase.Resting -> {
                Text(
                    text = stringResource(R.string.gym_exercise_rest),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progressOf(phase.remainingSeconds, phase.totalSeconds) },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    TimerText(
                        seconds = phase.remainingSeconds,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
                Text(
                    text = stringResource(
                        R.string.gym_exercise_next_set,
                        phase.nextSetIndex + 1,
                        state.totalSets
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetLabel(setIndex: Int, totalSets: Int) {
    Text(
        text = stringResource(R.string.gym_exercise_set_x_of_y, setIndex + 1, totalSets),
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

private fun progressOf(remainingSeconds: Int, totalSeconds: Int): Float =
    if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.gym_exercise_loading))
    }
}

@Composable
private fun NotFoundContent(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.gym_exercise_not_found),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBackPressed) {
            Text(text = stringResource(R.string.back))
        }
    }
}

@Composable
private fun ExitConfirmationDialog(
    completedSets: Int,
    totalSets: Int,
    onConfirmExit: () -> Unit,
    onDismissExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissExit,
        title = { Text(text = stringResource(R.string.gym_exercise_exit_title)) },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.gym_exercise_exit_message,
                    totalSets,
                    completedSets,
                    totalSets
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmExit) {
                Text(text = stringResource(R.string.gym_exercise_exit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissExit) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}
