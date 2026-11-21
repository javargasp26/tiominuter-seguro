package com.javargasp.tiominutermvi.ui.workout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.ExerciseType

/**
 * Exercise-specific bottom buttons for the workout screen.
 */
@Composable
internal fun ExerciseBottomButtons(
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
                    isRestExercise = state.currentRoutineExercise.isRest,
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
        // Only the skip button once the reps are completed
        SingleSkipButton(onSkip = onSkip)
    } else {
        PrimaryAndSkipButtons(
            primaryText = R.string.complete_reps,
            onPrimary = onComplete,
            onSkip = onSkip
        )
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
            // Skip button during the ready countdown
            SingleSkipButton(onSkip = onSkip)
        }
        !isTimerRunning && remainingSeconds == 0 -> {
            // Timer completed - show next button
            PrimaryAndSkipButtons(
                primaryText = R.string.next_exercise,
                onPrimary = onComplete,
                onSkip = onSkip
            )
        }
        !isTimerRunning && remainingSeconds > 0 -> {
            // Timer not started - show start button (unless it's a rest exercise)
            if (isRestExercise) {
                // For rest exercises, just show skip button
                SingleSkipButton(onSkip = onSkip)
            } else {
                // For regular exercises, show Get Ready button
                PrimaryAndSkipButtons(
                    primaryText = R.string.get_ready,
                    onPrimary = onStartWithReady,
                    onSkip = onSkip
                )
            }
        }
        else -> {
            // Timer is running - show skip button
            SingleSkipButton(onSkip = onSkip)
        }
    }
}

/**
 * Primary (filled) + skip (outlined) side by side, each taking half of the row,
 * so both have the same size whatever the length of their labels.
 */
@Composable
private fun PrimaryAndSkipButtons(
    @StringRes primaryText: Int,
    onPrimary: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonMinHeight),
            contentPadding = ButtonPadding
        ) {
            ButtonLabel(stringResource(primaryText))
        }

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonMinHeight),
            contentPadding = ButtonPadding
        ) {
            ButtonLabel(stringResource(R.string.skip_exercise))
        }
    }
}

/** A single skip (outlined) button, as wide as the two-button row. */
@Composable
private fun SingleSkipButton(onSkip: () -> Unit) {
    OutlinedButton(
        onClick = onSkip,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ButtonMinHeight),
        contentPadding = ButtonPadding
    ) {
        ButtonLabel(stringResource(R.string.skip_exercise))
    }
}

/**
 * One-line, centered button label. A label that does not fit shrinks step by step
 * (down to [MinLabelFontSize]) and is ellipsized only if it still does not fit, so a
 * long translation never wraps or grows the button. Hidden until its size is settled.
 */
@Composable
private fun ButtonLabel(text: String) {
    val baseFontSize = LocalTextStyle.current.fontSize.takeIf { it != TextUnit.Unspecified } ?: 14.sp
    var fontSize by remember(text, baseFontSize) { mutableStateOf(baseFontSize) }
    var isSizeSettled by remember(text, baseFontSize) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = Modifier.drawWithContent { if (isSizeSettled) drawContent() },
        fontSize = fontSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > MinLabelFontSize.value) {
                fontSize = maxOf(fontSize.value * 0.9f, MinLabelFontSize.value).sp
            } else {
                isSizeSettled = true
            }
        }
    )
}

private val ButtonMinHeight = 48.dp
private val ButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
private val MinLabelFontSize = 11.sp
