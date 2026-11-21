package com.javargasp.tiominutermvi.ui.workout

import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.ui.components.share.LatestShareInGroupsSection
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.text
import com.javargasp.tiominutermvi.ui.dailyroutineselection.RestartDaysDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Screen displayed when the user completes a daily routine ([routineId]). After the last day it
 * offers "Start again from Day 1" ([WorkoutCompletionViewModel]).
 */
@Composable
fun WorkoutCompletionScreen(
    routineId: Int,
    onNavigateToHome: () -> Unit,
    onNavigateToDailySelection: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutCompletionViewModel = koinViewModel { parametersOf(routineId) }
) = MviComposable(
    viewModel = viewModel,
    onEffect = { effect ->
        when (effect) {
            WorkoutCompletionEffect.NavigateToDailySelection -> onNavigateToDailySelection()
        }
    }
) { state, onIntent ->
    if (state.showRestartDialog) {
        RestartDaysDialog(
            firstDay = state.firstDay,
            onConfirm = { onIntent(WorkoutCompletionIntent.OnRestartConfirmed) },
            onDismiss = { onIntent(WorkoutCompletionIntent.OnRestartDismissed) }
        )
    }
    WorkoutCompletionContent(
        state = state,
        onNavigateToHome = onNavigateToHome,
        onStartAgain = { onIntent(WorkoutCompletionIntent.OnStartAgainClicked) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutCompletionContent(
    state: WorkoutCompletionState,
    onNavigateToHome: () -> Unit,
    onStartAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(stringResource(R.string.workout_complete))
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Success message
            Text(
                text = stringResource(R.string.workout_complete),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Encouragement message
            Text(
                text = stringResource(R.string.great_job),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // The daily entry is recorded as this screen opens: the switch appears once it is
            LatestShareInGroupsSection(source = TrainingSource.DAILY)

            Spacer(modifier = Modifier.height(24.dp))

            if (state.offersStartAgain) {
                Text(
                    text = stringResource(R.string.daily_cycle_complete),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onStartAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.daily_start_again, state.firstDay.text()))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Home button
            Button(
                onClick = onNavigateToHome,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.back_to_home))
            }
        }
    }
}