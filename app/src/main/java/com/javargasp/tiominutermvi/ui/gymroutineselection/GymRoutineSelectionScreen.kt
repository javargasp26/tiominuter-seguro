package com.javargasp.tiominutermvi.ui.gymroutineselection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.domain.model.GymRoutineExercise
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.exerciseSummary
import com.javargasp.tiominutermvi.ui.components.localizedName
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import com.javargasp.tiominutermvi.ui.components.workoutName
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Gym Routine Selection screen: one card per routine, expandable to show its
 * exercises.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymRoutineSelectionScreen(
    onNavigateToGymRoutine: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GymRoutineSelectionViewModel = koinViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshErrorMessage = stringResource(R.string.gym_refresh_error_snackbar)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is GymRoutineSelectionEffect.NavigateToGymRoutine -> onNavigateToGymRoutine(effect.routineId)
                is GymRoutineSelectionEffect.NavigateBack -> onNavigateBack()
                is GymRoutineSelectionEffect.ShowRefreshError -> scope.launch {
                    snackbarHostState.showSnackbar(refreshErrorMessage)
                }
                is GymRoutineSelectionEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
            }
        }
    ) { state, onIntent ->

        LaunchedEffect(Unit) {
            onIntent(GymRoutineSelectionIntent.OnLoad)
        }
        // Every open and return to the foreground: lazy close of abandoned gym sessions
        LifecycleResumeEffect(Unit) {
            onIntent(GymRoutineSelectionIntent.OnResume)
            onPauseOrDispose { }
        }

        GymRoutineSelectionContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onToggleExpanded = { routineId ->
                onIntent(GymRoutineSelectionIntent.OnToggleExpanded(routineId))
            },
            onStartRoutine = { routineId ->
                onIntent(GymRoutineSelectionIntent.OnStartRoutine(routineId))
            },
            onRetry = {
                onIntent(GymRoutineSelectionIntent.OnRetry)
            },
            onBackPressed = {
                onIntent(GymRoutineSelectionIntent.OnBackPressed)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GymRoutineSelectionContent(
    state: GymRoutineSelectionState,
    snackbarHostState: SnackbarHostState,
    onToggleExpanded: (Int) -> Unit,
    onStartRoutine: (Int) -> Unit,
    onRetry: () -> Unit,
    onBackPressed: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.gym_routines_button)) },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                // Thin refresh bar only while refreshing with data on screen.
                if (state.isRefreshing && state.routines.isNotEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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
                state.errorMessage != null -> {
                    GymErrorState(
                        message = stringResource(R.string.gym_refresh_failed),
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.routines.isEmpty() -> {
                    GymEmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    GymRoutineList(
                        state = state,
                        onToggleExpanded = onToggleExpanded,
                        onStartRoutine = onStartRoutine
                    )
                }
            }
        }
    }
}

@Composable
private fun GymRoutineList(
    state: GymRoutineSelectionState,
    onToggleExpanded: (Int) -> Unit,
    onStartRoutine: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.routines, key = { it.routineId }) { routine ->
            GymRoutineCard(
                routine = routine,
                isExpanded = state.expandedRoutineIds.contains(routine.routineId),
                onToggleExpanded = { onToggleExpanded(routine.routineId) },
                onStartRoutine = { onStartRoutine(routine.routineId) }
            )
        }
    }
}

@Composable
private fun GymRoutineCard(
    routine: GymRoutine,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onStartRoutine: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = routine.workoutName(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Clickable "See routine" row with rotating chevron. The chevron is
            // decorative: the row's text, click label and state describe it.
            val toggleLabel = stringResource(
                if (isExpanded) R.string.gym_hide_exercises else R.string.gym_show_exercises
            )
            val expandedState = stringResource(
                if (isExpanded) R.string.gym_state_expanded else R.string.gym_state_collapsed
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClickLabel = toggleLabel,
                        role = Role.Button,
                        onClick = onToggleExpanded
                    )
                    .semantics { stateDescription = expandedState }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.see_routine),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = tween(200),
                        label = "chevronRotation"
                    ).value)
                )
            }

            // Expanded exercise list.
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (routine.exercises.isEmpty()) {
                        Text(
                            text = stringResource(R.string.gym_routine_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    routine.exercises.forEach { exercise ->
                        GymExerciseRow(exercise = exercise)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onStartRoutine,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.gym_start_routine))
            }
        }
    }
}

/**
 * One exercise row: name on the left (fades out at the end only when it
 * overflows), values on the right (never truncated).
 */
@Composable
private fun GymExerciseRow(exercise: GymRoutineExercise) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val exerciseName = exercise.exercise?.localizedName()
            ?: stringResource(R.string.unknown_exercise)

        FadingText(
            text = exerciseName,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = exerciseSummary(exercise),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FadingText(
    text: String,
    modifier: Modifier = Modifier
) {
    var hasVisualOverflow by remember { mutableStateOf(false) }
    val fadeBrush = remember {
        Brush.horizontalGradient(
            0f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result -> hasVisualOverflow = result.hasVisualOverflow },
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithContent {
            if (hasVisualOverflow) {
                drawContent()
                drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
            } else {
                drawContent()
            }
        }
    )
}

@Composable
private fun GymEmptyState(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.gym_empty_catalog),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun GymErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}
