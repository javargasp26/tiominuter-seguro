package com.javargasp.tiominutermvi.ui.program.detail

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.ProgramWeekDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutDetail
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkoutKind
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramNotFound
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.currentLocale
import com.javargasp.tiominutermvi.ui.program.common.durationLabel
import com.javargasp.tiominutermvi.ui.program.common.focusLabel
import com.javargasp.tiominutermvi.ui.program.common.formatPercent
import com.javargasp.tiominutermvi.ui.program.common.goalLabel
import com.javargasp.tiominutermvi.ui.program.common.workoutSummary
import com.javargasp.tiominutermvi.ui.program.common.workoutTitle
import com.javargasp.tiominutermvi.ui.theme.extendedColors
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.javargasp.tiominutermvi.ui.components.showSessionSaved
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext

/** One program: a section per week with its workouts and completion. */
@Composable
fun ProgramDetailScreen(
    programId: Long,
    onNavigateToWorkout: (kind: ProgramWorkoutKind, week: Int, dayIndex: Int) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProgramDetailViewModel = koinViewModel { parametersOf(programId) }
) {
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is ProgramDetailEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
                is ProgramDetailEffect.NavigateToWorkout ->
                    onNavigateToWorkout(effect.kind, effect.week, effect.dayIndex)
                ProgramDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        // Every open and return to the foreground: lazy close of abandoned program workouts
        LifecycleResumeEffect(Unit) {
            onIntent(ProgramDetailIntent.OnScreenResumed)
            onPauseOrDispose { }
        }
        ProgramDetailContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramDetailContent(
    state: ProgramDetailState,
    onIntent: (ProgramDetailIntent) -> Unit
) {
    val program = state.program
    val onBack = { onIntent(ProgramDetailIntent.OnBackPressed) }
    Scaffold(
        topBar = {
            if (program != null) {
                ProgramTopBar(
                    title = goalLabel(program.summary.profile.goal),
                    subtitle = durationLabel(program.summary.profile),
                    onBack = onBack
                )
            } else {
                ProgramTopBar(title = stringResource(R.string.program_list_title), onBack = onBack)
            }
        }
    ) { padding ->
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isNotFound || program == null -> ProgramNotFound(
                text = stringResource(R.string.program_program_not_found),
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "focus") {
                    Text(
                        text = stringResource(R.string.program_focus_value, focusLabel(program.summary.profile.focus)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                program.weeks.forEach { week ->
                    item(key = "week-${week.weekNumber}") { WeekHeader(week) }
                    items(week.workouts, key = { "w-${it.week}-${it.dayIndex}" }) { workout ->
                        WorkoutCard(
                            workout = workout,
                            onClick = { onIntent(ProgramDetailIntent.OnWorkoutClicked(workout.week, workout.dayIndex)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(week: ProgramWeekDetail) {
    val percent = formatPercent(week.completedWorkouts, week.workouts.size, currentLocale())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.program_week_header, week.weekNumber, percent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (week.isDeload) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.program_deload),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkoutCard(workout: ProgramWorkoutDetail, onClick: () -> Unit) {
    val stateText = stringResource(
        if (workout.isCompleted) R.string.program_completed else R.string.program_not_completed
    )
    val colors = if (workout.isCompleted) {
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
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = workoutTitle(workout.workout), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = workoutSummary(workout.workout), style = MaterialTheme.typography.bodyMedium)
            }
            if (workout.isCompleted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.success
                )
            }
        }
    }
}
