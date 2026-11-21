package com.javargasp.tiominutermvi.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.feedback.WorkoutFeedback
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.KeepScreenOn
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Workout Screen that displays and manages workout execution. Keeps the screen on
 * while visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    routineId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToCompletion: () -> Unit = {},
    viewModel: WorkoutViewModel = koinViewModel { parametersOf(routineId) },
    feedback: WorkoutFeedback = koinInject()
) {
    KeepScreenOn()
    val context = LocalContext.current

    // Remember flags to prevent multiple triggers
    var hasPlayedSound by remember { mutableStateOf(false) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is WorkoutEffect.NavigateBack -> {
                    onNavigateBack()
                }
                is WorkoutEffect.NavigateToHome -> {
                    onNavigateToHome()
                }
                is WorkoutEffect.NavigateToCompletion -> {
                    onNavigateToCompletion()
                }
                is WorkoutEffect.PlayCompletionSound -> {
                    if (!hasPlayedSound) {
                        hasPlayedSound = true
                        feedback.playCompletionSound()
                    }
                }
                is WorkoutEffect.TriggerHapticFeedback -> {
                    if (!hasTriggeredHaptic) {
                        hasTriggeredHaptic = true
                        feedback.vibrate()
                    }
                }
                is WorkoutEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
            }
        }
    ) { state, onIntent ->

        // Handle initial load
        LaunchedEffect(Unit) {
            onIntent(WorkoutIntent.OnLoad)
        }

        // A REPS exercise counts as active time only while the screen is visible
        LifecycleResumeEffect(Unit) {
            onIntent(WorkoutIntent.OnScreenVisible)
            onPauseOrDispose { onIntent(WorkoutIntent.OnScreenHidden) }
        }

        // Reset flags when exercise changes
        LaunchedEffect(state.currentExerciseIndex) {
            hasPlayedSound = false
            hasTriggeredHaptic = false
        }

        WorkoutContent(
            state = state,
            onIntent = onIntent
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutContent(
    state: WorkoutState,
    onIntent: (WorkoutIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isWorkoutComplete) {
                            stringResource(R.string.workout_complete)
                        } else {
                            stringResource(R.string.workout)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(WorkoutIntent.OnBackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null -> {
                    Text(
                        text = state.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.isWorkoutComplete -> {
                    WorkoutCompleteContent(
                        modifier = Modifier.align(Alignment.Center),
                        onNavigateToHome = { onIntent(WorkoutIntent.OnFinishWorkout) }
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Scrollable content
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                .padding(bottom = 80.dp), // Add padding for buttons
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Exercise content (without buttons)
                            ExerciseContentOnly(
                                state = state,
                                onIntent = onIntent
                            )
                        }

                        // Fixed buttons at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            ExerciseBottomButtons(
                                state = state,
                                onIntent = onIntent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCompleteContent(
    modifier: Modifier = Modifier,
    onNavigateToHome: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.workout_complete),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.great_job),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onNavigateToHome,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.back_to_home))
        }
    }
}
