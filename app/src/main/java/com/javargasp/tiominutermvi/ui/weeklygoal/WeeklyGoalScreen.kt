package com.javargasp.tiominutermvi.ui.weeklygoal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.goal.MIN_COUNTED_DURATION_SECONDS
import com.javargasp.tiominutermvi.ui.base.MviComposable
import org.koin.androidx.compose.koinViewModel

/** Weekly goal: how many workouts per week (1..14), applied from the current week. */
@Composable
fun WeeklyGoalScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeeklyGoalViewModel = koinViewModel()
) {
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is WeeklyGoalEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        WeeklyGoalContent(state = state, onIntent = onIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeeklyGoalContent(
    state: WeeklyGoalState,
    onIntent: (WeeklyGoalIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekly_goal_title)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(WeeklyGoalIntent.OnBackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.weekly_goal_question),
                style = MaterialTheme.typography.titleMedium
            )
            val minMinutes = MIN_COUNTED_DURATION_SECONDS / 60
            WorkoutsStepper(
                workouts = state.selectedWorkouts,
                canDecrement = state.canDecrement,
                canIncrement = state.canIncrement,
                onDecrement = { onIntent(WeeklyGoalIntent.OnDecrement) },
                onIncrement = { onIntent(WeeklyGoalIntent.OnIncrement) }
            )
            Text(
                text = pluralStringResource(R.plurals.weekly_goal_explanation, minMinutes, minMinutes),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.weekly_goal_applies_from_this_week),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.saveFailed) {
                ErrorText(stringResource(R.string.weekly_goal_save_failed))
            }
            Button(
                onClick = { onIntent(WeeklyGoalIntent.OnSave) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(stringResource(R.string.weekly_goal_save))
            }
        }
    }
}

/** − [workouts per week] +, with 48dp buttons; the value is announced when it changes. */
@Composable
private fun WorkoutsStepper(
    workouts: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    val fewerLabel = stringResource(R.string.weekly_goal_fewer)
    val moreLabel = stringResource(R.string.weekly_goal_more)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        FilledTonalIconButton(
            onClick = onDecrement,
            enabled = canDecrement,
            modifier = Modifier.size(56.dp)
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { contentDescription = fewerLabel }
            )
        }
        Text(
            text = pluralStringResource(R.plurals.weekly_goal_workouts_per_week, workouts, workouts),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        FilledTonalIconButton(
            onClick = onIncrement,
            enabled = canIncrement,
            modifier = Modifier.size(56.dp)
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { contentDescription = moreLabel }
            )
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}
