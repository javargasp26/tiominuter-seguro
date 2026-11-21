package com.javargasp.tiominutermvi.ui.program.strength

import com.javargasp.tiominutermvi.ui.components.share.ShareInGroupsSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.StrengthSlot
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoSection
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.program.common.ProgramConfirmDialog
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramNotFound
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.isSpanish
import com.javargasp.tiominutermvi.ui.program.common.programExerciseSummary
import com.javargasp.tiominutermvi.ui.program.common.workoutTitle
import com.javargasp.tiominutermvi.ui.theme.extendedColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import androidx.lifecycle.compose.LifecycleResumeEffect

/** A strength workout of a program: its exercises, progress and reset. */
@Composable
fun ProgramStrengthScreen(
    programId: Long,
    week: Int,
    dayIndex: Int,
    onNavigateToExercise: (position: Int) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProgramStrengthViewModel = koinViewModel { parametersOf(programId, week, dayIndex) }
) {
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is ProgramStrengthEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
                is ProgramStrengthEffect.NavigateToExercise -> onNavigateToExercise(effect.position)
                ProgramStrengthEffect.NavigateBack -> onNavigateBack()
                is ProgramStrengthEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
            }
        }
    ) { state, onIntent ->
        // Every open and return to the foreground: lazy close of abandoned program workouts
        LifecycleResumeEffect(Unit) {
            onIntent(ProgramStrengthIntent.OnScreenResumed)
            onPauseOrDispose { }
        }
        ProgramStrengthContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramStrengthContent(
    state: ProgramStrengthState,
    onIntent: (ProgramStrengthIntent) -> Unit
) {
    val workout = state.workout
    val onBack = { onIntent(ProgramStrengthIntent.OnBackPressed) }
    Scaffold(
        topBar = {
            ProgramTopBar(
                title = if (workout != null) workoutTitle(workout) else "",
                subtitle = stringResource(R.string.program_week_title, state.week),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isNotFound || workout == null -> ProgramNotFound(
                text = stringResource(R.string.program_not_found),
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.program_exercises_completed,
                                state.totalCount,
                                state.completedCount,
                                state.totalCount
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        LinearProgressIndicator(
                            progress = { if (state.totalCount > 0) state.completedCount.toFloat() / state.totalCount else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.canReset) {
                            OutlinedButton(onClick = { onIntent(ProgramStrengthIntent.OnResetClicked) }) {
                                Text(stringResource(R.string.program_reset))
                            }
                        }
                    }
                }
                items(workout.slots, key = { it.position }) { slot ->
                    ExerciseCard(
                        slot = slot,
                        exercise = state.exercises[slot.exerciseId],
                        isCompleted = slot.position in state.completedPositions,
                        onClick = { onIntent(ProgramStrengthIntent.OnExerciseClicked(slot.position)) }
                    )
                }
            }
        }
    }

    if (state.showResetConfirmation) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_reset_title),
            text = stringResource(R.string.program_reset_text),
            confirmLabel = stringResource(R.string.program_reset),
            dismissLabel = stringResource(R.string.program_cancel),
            onConfirm = { onIntent(ProgramStrengthIntent.OnConfirmReset) },
            onDismiss = { onIntent(ProgramStrengthIntent.OnDismissReset) }
        )
    }
    if (state.showWorkoutCompleted) {
        AlertDialog(
            onDismissRequest = { onIntent(ProgramStrengthIntent.OnWorkoutCompletedDismissed) },
            title = { Text(stringResource(R.string.program_workout_completed_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.program_workout_completed_text))
                    CompletionPhotoSection(
                        photo = state.completionPhoto,
                        onPhotoSelected = { onIntent(ProgramStrengthIntent.OnCompletionPhotoSelected(it)) }
                    )
                    ShareInGroupsSection(entryId = state.completionPhoto.entryId)
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(ProgramStrengthIntent.OnWorkoutCompletedDismissed) }) {
                    Text(stringResource(R.string.program_back_to_program))
                }
            }
        )
    }
}

@Composable
private fun ExerciseCard(
    slot: StrengthSlot,
    exercise: ProgramExercise?,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val stateText = stringResource(if (isCompleted) R.string.program_completed else R.string.program_not_completed)
    val colors = if (isCompleted) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.extendedColors.successContainer,
            contentColor = MaterialTheme.extendedColors.onSuccessContainer
        )
    } else {
        CardDefaults.cardColors()
    }
    Card(
        onClick = onClick,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = stateText }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = exercise?.displayName(isSpanish()) ?: stringResource(R.string.program_unknown_exercise),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(text = programExerciseSummary(slot), style = MaterialTheme.typography.bodyMedium)
            }
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.success
                )
            }
        }
    }
}
