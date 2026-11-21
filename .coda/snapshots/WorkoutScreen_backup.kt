package com.javargasp.tiominutermvi.ui.workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.ui.base.CollectEffects
import com.javargasp.tiominutermvi.ui.base.MviComposable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Workout Screen that displays and manages workout execution.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    routineId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToCompletion: () -> Unit = {},
    viewModel: WorkoutViewModel = koinViewModel { parametersOf(routineId) }
) {
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
                        playCompletionSound(context)
                    }
                }
                is WorkoutEffect.TriggerHapticFeedback -> {
                    if (!hasTriggeredHaptic) {
                        hasTriggeredHaptic = true
                        triggerHapticFeedback(context)
                    }
                }
            }
        }
    ) { state, onIntent ->
        
        // Handle initial load
        LaunchedEffect(Unit) {
            onIntent(WorkoutIntent.OnLoad)
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
private fun WorkoutContent(
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
                            imageVector = Icons.Default.ArrowBack,
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

@Composable
private fun ExerciseContentOnly(
    state: WorkoutState,
    onIntent: (WorkoutIntent) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Exercise name
        state.currentExercise?.let { exercise ->
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            // Exercise animation
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("file:///android_asset/animations/${exercise.animation}")
                    .build(),
                contentDescription = "${exercise.name} animation",
                modifier = Modifier
                    .size(200.dp)
                    .padding(vertical = 16.dp),
                contentScale = ContentScale.Fit
            )
                        
            // Exercise type and amount
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (state.currentRoutineExercise?.type) {
                        ExerciseType.REPS -> stringResource(R.string.reps)
                        ExerciseType.TIME -> stringResource(R.string.seconds)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = state.currentRoutineExercise?.amount?.toString() ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Progress indicator
            Text(
                text = stringResource(
                    R.string.exercise_progress,
                    state.currentExerciseIndex + 1,
                    state.totalExercises
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Timer display for TIME exercises
            if (state.currentRoutineExercise?.type == ExerciseType.TIME) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = when {
                        state.isReadyCountdown -> MaterialTheme.colorScheme.tertiaryContainer
                        state.isTimerRunning -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    val displayText = when {
                        state.isReadyCountdown -> {
                            Text(
                                text = "GET READY\n${state.readyCountdownSeconds}",
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            val minutes = state.timerRemainingSeconds / 60
                            val seconds = state.timerRemainingSeconds % 60
                            val timeText = String.format("%02d:%02d", minutes, seconds)
                            
                            Text(
                                text = timeText,
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.displayLarge,
                                color = if (state.isTimerRunning) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    displayText
                }
                
                // Status text
                when {
                    state.isReadyCountdown -> {
                        Text(
                            text = "Prepare yourself!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    !state.isTimerRunning && state.timerRemainingSeconds == 0 -> {
                        // Timer completed
                        Text(
                            text = stringResource(R.string.time_up),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    state.isTimerRunning -> {
                        Text(
                            text = stringResource(R.string.timer_running),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseBottomButtons(
    state: WorkoutState,
    onIntent: (WorkoutIntent) -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Exercise-specific buttons
        when (state.currentRoutineExercise?.type) {
            ExerciseType.REPS -> {
                RepsExerciseButtons(
                    targetReps = state.currentRoutineExercise.amount,
                    completedReps = state.completedReps,
                    onComplete = { onIntent(WorkoutIntent.OnCompleteReps) },
                    onSkip = { onIntent(WorkoutIntent.OnSkipExercise) }
                )
            }
            ExerciseType.TIME -> {
                TimeExerciseButtons(
                    durationSeconds = state.currentRoutineExercise.amount,
                    remainingSeconds = state.timerRemainingSeconds,
                    isTimerRunning = state.isTimerRunning,
                    isReadyCountdown = state.isReadyCountdown,
                    readyCountdownSeconds = state.readyCountdownSeconds,
                    isRestExercise = state.currentExercise?.exerciseId == 13,
                    onComplete = { onIntent(WorkoutIntent.OnNextExercise) },
                    onSkip = { onIntent(WorkoutIntent.OnSkipExercise) },
                    onStartWithReady = { onIntent(WorkoutIntent.OnStartTimerWithReady) }
                )
            }
            null -> {
                // Handle null case
            }
        }
        
        // Bottom padding
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RepsExerciseButtons(
    targetReps: Int,
    completedReps: Int,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    if (completedReps >= targetReps) {
        // Only show skip button when reps are completed - center it
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(
                onClick = onSkip
            ) {
                Text(stringResource(R.string.skip_exercise))
            }
        }
    } else {
        // Show both buttons - space them evenly
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onComplete
            ) {
                Text(stringResource(R.string.complete_reps))
            }
            
            OutlinedButton(
                onClick = onSkip
            ) {
                Text(stringResource(R.string.skip_exercise))
            }
        }
    }
}

@Composable
private fun TimeExerciseButtons(
    durationSeconds: Int,
    remainingSeconds: Int,
    isTimerRunning: Boolean,
    isReadyCountdown: Boolean = false,
    readyCountdownSeconds: Int = 0,
    isRestExercise: Boolean = false,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onStartWithReady: () -> Unit = {}
) {
    // Show buttons based on state
    when {
        isReadyCountdown -> {
            // Show skip button during ready countdown - center it
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(stringResource(R.string.skip_exercise))
                }
            }
        }
        !isTimerRunning && remainingSeconds == 0 -> {
            // Timer completed - show next button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.width(140.dp)
                ) {
                    Text(stringResource(R.string.next_exercise))
                }
                
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.width(140.dp)
                ) {
                    Text(stringResource(R.string.skip_exercise))
                }
            }
        }
        !isTimerRunning && remainingSeconds > 0 -> {
            // Timer not started - show start button (unless it's a rest exercise)
            if (isRestExercise) {
                // For rest exercises, just show skip button - center it
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.width(200.dp)
                    ) {
                        Text(stringResource(R.string.skip_exercise))
                    }
                }
            } else {
                // For regular exercises, show Get Ready button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onStartWithReady,
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text("Get Ready")
                    }
                    
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text(stringResource(R.string.skip_exercise))
                    }
                }
            }
        }
        else -> {
            // Timer is running - show skip button - center it
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(stringResource(R.string.skip_exercise))
                }
            }
        }
    }
}

@Composable
private fun TimeExerciseContent(
    durationSeconds: Int,
    remainingSeconds: Int,
    isTimerRunning: Boolean,
    isReadyCountdown: Boolean = false,
    readyCountdownSeconds: Int = 0,
    isRestExercise: Boolean = false,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onStartWithReady: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Content at the top/middle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Timer display or Ready countdown
        Surface(
            shape = MaterialTheme.shapes.large,
            color = when {
                isReadyCountdown -> MaterialTheme.colorScheme.tertiaryContainer
                isTimerRunning -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            val displayText = when {
                isReadyCountdown -> {
                    Text(
                        text = "GET READY\n$readyCountdownSeconds",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    val timeText = String.format("%02d:%02d", minutes, seconds)
                    
                    Text(
                        text = timeText,
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (isTimerRunning) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            displayText
        }
        
        when {
            isReadyCountdown -> {
                Text(
                    text = "Prepare yourself!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            !isTimerRunning && remainingSeconds == 0 -> {
                // Timer completed
                Text(
                    text = stringResource(R.string.time_up),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            isTimerRunning -> {
                Text(
                    text = stringResource(R.string.timer_running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        }
        
        // Buttons at the bottom with padding
        TimeExerciseButtons(
            durationSeconds = durationSeconds,
            remainingSeconds = remainingSeconds,
            isTimerRunning = isTimerRunning,
            isReadyCountdown = isReadyCountdown,
            readyCountdownSeconds = readyCountdownSeconds,
            isRestExercise = isRestExercise,
            onComplete = onComplete,
            onSkip = onSkip,
            onStartWithReady = onStartWithReady
        )
    }
}


/**
 * Plays a completion sound using Android's ToneGenerator.
 * This uses the system's default sound pool and doesn't require external audio files.
 */
private fun playCompletionSound(context: Context) {
    try {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    } catch (e: Exception) {
        // Silently fail if sound cannot be played
    }
}

/**
 * Triggers haptic feedback for exercise completion.
 * Uses the device's vibrator to provide tactile feedback.
 */
private fun triggerHapticFeedback(context: Context) {
    try {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    200, // 200ms vibration
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    } catch (e: Exception) {
        // Silently fail if vibration is not available
    }
}