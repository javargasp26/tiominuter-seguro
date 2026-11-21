package com.javargasp.tiominutermvi.ui.dailyroutineselection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.ui.theme.extendedColors
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.base.MviComposable
import org.koin.androidx.compose.koinViewModel
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.components.text
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import android.content.Context

/**
 * Daily Routine Selection Screen that displays available workout routines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyRoutineSelectionScreen(
    onNavigateToWorkout: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DailyRoutineSelectionViewModel = koinViewModel()
) {
    val context = LocalContext.current
    DailyRoutineSelectionScreenContent(viewModel, context, onNavigateToWorkout, onNavigateBack)
}

@Composable
private fun DailyRoutineSelectionScreenContent(
    viewModel: DailyRoutineSelectionViewModel,
    context: Context,
    onNavigateToWorkout: (Int) -> Unit,
    onNavigateBack: () -> Unit
) = MviComposable(
    viewModel = viewModel,
    onEffect = { effect ->
        when (effect) {
            is DailyRoutineSelectionEffect.NavigateToWorkout -> {
                onNavigateToWorkout(effect.routineId)
            }
            is DailyRoutineSelectionEffect.NavigateBack -> {
                onNavigateBack()
            }
            is DailyRoutineSelectionEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
        }
    }
) { state, onIntent ->
    
    // Handle initial load
    LaunchedEffect(Unit) {
        onIntent(DailyRoutineSelectionIntent.OnLoad)
    }

    // Every open and return to the foreground: lazy close of an abandoned daily workout
    LifecycleResumeEffect(Unit) {
        onIntent(DailyRoutineSelectionIntent.OnResume)
        onPauseOrDispose { }
    }
    
    
    DailyRoutineSelectionContent(state = state, onIntent = onIntent)
}

/** Stateless: the previews ([DailyRoutineSelectionPreviews]) render it from plain states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DailyRoutineSelectionContent(
    state: DailyRoutineSelectionState,
    onIntent: (DailyRoutineSelectionIntent) -> Unit
) {
    if (state.showRestartDialog) {
        RestartDaysDialog(
            firstDay = state.dayName(1),
            onConfirm = { onIntent(DailyRoutineSelectionIntent.OnRestartConfirmed) },
            onDismiss = { onIntent(DailyRoutineSelectionIntent.OnRestartDismissed) }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_routine)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(DailyRoutineSelectionIntent.OnBackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (state.canRestart) {
                        TextButton(onClick = { onIntent(DailyRoutineSelectionIntent.OnRestartClicked) }) {
                            Text(stringResource(R.string.daily_restart_action))
                        }
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
                state.isLoading || (state.progress == null && state.error == null) -> {
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
                state.routines.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_routines_available),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isCycleComplete) {
                            item {
                                CycleCompleteCard(
                                    firstDay = state.dayName(1),
                                    onStartAgain = { onIntent(DailyRoutineSelectionIntent.OnRestartClicked) }
                                )
                            }
                        }
                        items(state.routines, key = { it.routineId }) { routine ->
                            RoutineCard(
                                routine = routine,
                                status = state.statusOf(routine.routineId),
                                isNext = routine.routineId == state.nextDay && !state.isCycleComplete,
                                previousDay = state.dayName(routine.routineId - 1),
                                onClick = { onIntent(DailyRoutineSelectionIntent.OnRoutineSelected(routine.routineId)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** After the last day: "You completed every day!" and "Start again from Day 1" (asks first). */
@Composable
private fun CycleCompleteCard(firstDay: WorkoutName.Daily, onStartAgain: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.extendedColors.successContainer,
            contentColor = MaterialTheme.extendedColors.onSuccessContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.daily_cycle_complete),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Button(onClick = onStartAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.daily_start_again, firstDay.text()))
            }
        }
    }
}

/**
 * A day: its name, its exercises and its state. A locked day can't be opened and says which day
 * unlocks it; the state is also read by TalkBack ("Locked", "Completed", "Available").
 */
@Composable
private fun RoutineCard(
    routine: com.javargasp.tiominutermvi.domain.model.Routine,
    status: DailyDayStatus,
    isNext: Boolean,
    previousDay: WorkoutName.Daily,
    onClick: () -> Unit
) {
    val stateText = stringResource(
        when (status) {
            DailyDayStatus.COMPLETED -> R.string.daily_state_completed
            DailyDayStatus.AVAILABLE -> R.string.daily_state_available
            DailyDayStatus.LOCKED -> R.string.daily_state_locked
        }
    )
    val locked = status == DailyDayStatus.LOCKED
    val colors = if (isNext) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    val modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) { stateDescription = stateText }
    val content: @Composable ColumnScope.() -> Unit = {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WorkoutNameResolver.dailyName(routine).text(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        id = R.string.exercises_count,
                        routine.exercises.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    locked -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.daily_locked_hint, previousDay.text()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    isNext -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.daily_available_today),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            // Decorative: the state is in the card's stateDescription
            when (status) {
                DailyDayStatus.COMPLETED -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.success
                )
                DailyDayStatus.LOCKED -> Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DailyDayStatus.AVAILABLE -> Unit
            }
        }
    }
    if (locked) {
        Card(modifier = modifier.alpha(LOCKED_ALPHA), colors = colors, content = content)
    } else {
        Card(onClick = onClick, modifier = modifier, colors = colors, content = content)
    }
}

private const val LOCKED_ALPHA = 0.6f
