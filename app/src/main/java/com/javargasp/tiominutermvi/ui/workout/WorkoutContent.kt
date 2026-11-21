package com.javargasp.tiominutermvi.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.ExerciseType
import com.javargasp.tiominutermvi.ui.components.ExerciseAnimation
import com.javargasp.tiominutermvi.ui.components.TimerText
import com.javargasp.tiominutermvi.ui.components.localizedName

/**
 * Exercise content (name, animation, amount, progress, timer) for the workout
 * screen, without buttons.
 */
@Composable
internal fun ExerciseContentOnly(
    state: WorkoutState,
    onIntent: (WorkoutIntent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Exercise name
        state.currentExercise?.let { exercise ->
            val exerciseName = exercise.localizedName()
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Exercise animation
            ExerciseAnimation(
                animation = exercise.animation,
                contentDescription = stringResource(
                    R.string.exercise_animation_description,
                    exerciseName
                ),
                modifier = Modifier
                    .size(200.dp)
                    .padding(vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

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
                    if (state.isReadyCountdown) {
                        Text(
                            text = stringResource(R.string.get_ready_banner)
                                + "\n${state.readyCountdownSeconds}",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        TimerText(
                            seconds = state.timerRemainingSeconds,
                            modifier = Modifier.padding(24.dp),
                            color = if (state.isTimerRunning) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Status text
                when {
                    state.isReadyCountdown -> {
                        Text(
                            text = stringResource(R.string.prepare_yourself),
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
