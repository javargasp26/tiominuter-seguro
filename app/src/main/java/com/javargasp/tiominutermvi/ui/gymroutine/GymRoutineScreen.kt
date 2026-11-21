package com.javargasp.tiominutermvi.ui.gymroutine

import com.javargasp.tiominutermvi.ui.components.share.ShareInGroupsSection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.exerciseSummary
import com.javargasp.tiominutermvi.ui.components.localizedName
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoSection
import com.javargasp.tiominutermvi.ui.components.photo.CompletionPhotoState
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import com.javargasp.tiominutermvi.ui.theme.extendedColors
import com.javargasp.tiominutermvi.ui.components.workoutName
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Gym Routine screen: shows one card per exercise of the routine with its
 * completion state, plus progress and reset controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymRoutineScreen(
    routineId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToExercise: (routineId: Int, position: Int) -> Unit,
    viewModel: GymRoutineViewModel = koinViewModel { parametersOf(routineId) }
) {
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GymRoutineEffect.NavigateToExercise ->
                    onNavigateToExercise(effect.routineId, effect.position)
                is GymRoutineEffect.NavigateBack -> onNavigateBack()
                is GymRoutineEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
                is GymRoutineEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
            }
        }
    ) { state, onIntent ->

        LaunchedEffect(Unit) {
            onIntent(GymRoutineIntent.OnLoad)
        }
        // Every open and return to the foreground: lazy close of abandoned gym sessions
        LifecycleResumeEffect(Unit) {
            onIntent(GymRoutineIntent.OnResume)
            onPauseOrDispose { }
        }

        GymRoutineContent(
            state = state,
            onExerciseClicked = { position ->
                onIntent(GymRoutineIntent.OnExerciseClicked(position))
            },
            onResetClicked = {
                onIntent(GymRoutineIntent.OnResetClicked)
            },
            onConfirmReset = {
                onIntent(GymRoutineIntent.OnConfirmReset)
            },
            onDismissReset = {
                onIntent(GymRoutineIntent.OnDismissReset)
            },
            onRoutineCompletedDismissed = {
                onIntent(GymRoutineIntent.OnRoutineCompletedDismissed)
            },
            onBackPressed = {
                onIntent(GymRoutineIntent.OnBackPressed)
            },
            onCompletionPhotoSelected = { source ->
                onIntent(GymRoutineIntent.OnCompletionPhotoSelected(source))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GymRoutineContent(
    state: GymRoutineState,
    onExerciseClicked: (Int) -> Unit,
    onResetClicked: () -> Unit,
    onConfirmReset: () -> Unit,
    onDismissReset: () -> Unit,
    onRoutineCompletedDismissed: () -> Unit,
    onBackPressed: () -> Unit,
    onCompletionPhotoSelected: (TrainingPhotoSource) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = state.routine?.workoutName() ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onResetClicked,
                        enabled = state.canReset
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.gym_routine_reset_action_description)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(Modifier.padding(paddingValues))
            state.isNotFound -> NotFoundContent(
                onBackPressed = onBackPressed,
                modifier = Modifier.padding(paddingValues)
            )
            state.totalCount == 0 -> EmptyRoutineContent(Modifier.padding(paddingValues))
            else -> ExerciseListContent(
                state = state,
                onExerciseClicked = onExerciseClicked,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }

    if (state.showResetConfirmation) {
        ResetConfirmationDialog(
            onConfirmReset = onConfirmReset,
            onDismissReset = onDismissReset
        )
    }

    if (state.showRoutineCompleted) {
        RoutineCompletedDialog(
            photo = state.completionPhoto,
            onPhotoSelected = onCompletionPhotoSelected,
            onDismiss = onRoutineCompletedDismissed
        )
    }
}

@Composable
private fun ExerciseListContent(
    state: GymRoutineState,
    onExerciseClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val completedCount = state.completedCount
        val totalCount = state.totalCount
        Text(
            text = pluralStringResource(
                R.plurals.gym_routine_completed_count,
                totalCount,
                completedCount,
                totalCount
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LinearProgressIndicator(
            progress = { if (totalCount > 0) completedCount.toFloat() / totalCount else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = state.routine?.exercises ?: emptyList(),
                key = { it.position }
            ) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    isCompleted = exercise.position in state.validCompletedPositions,
                    onClick = { onExerciseClicked(exercise.position) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: GymRoutineExercise,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isCompleted) {
        MaterialTheme.extendedColors.successContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    // Announce the completion state to TalkBack, not only through color and
    // the (decorative) check icon.
    val completionState = stringResource(
        if (isCompleted) R.string.gym_state_completed else R.string.gym_state_not_completed
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = completionState },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exercise?.localizedName() ?: stringResource(R.string.unknown_exercise),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = exerciseSummary(exercise),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCompleted) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.success
                )
            }
        }
    }
}

@Composable
private fun EmptyRoutineContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.gym_routine_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = stringResource(R.string.gym_routine_loading))
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
            text = stringResource(R.string.gym_routine_not_found),
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
private fun ResetConfirmationDialog(
    onConfirmReset: () -> Unit,
    onDismissReset: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissReset,
        title = { Text(text = stringResource(R.string.gym_routine_reset_progress)) },
        text = { Text(text = stringResource(R.string.gym_routine_reset_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirmReset) {
                Text(text = stringResource(R.string.gym_routine_reset))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissReset) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

/** "Routine completed!" with an optional photo of this completion (see [CompletionPhotoSection]). */
@Composable
private fun RoutineCompletedDialog(
    photo: CompletionPhotoState,
    onPhotoSelected: (TrainingPhotoSource) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.gym_routine_completed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = stringResource(R.string.gym_routine_completed_message))
                CompletionPhotoSection(photo = photo, onPhotoSelected = onPhotoSelected)
                ShareInGroupsSection(entryId = photo.entryId)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.gym_routine_finish))
            }
        }
    )
}
