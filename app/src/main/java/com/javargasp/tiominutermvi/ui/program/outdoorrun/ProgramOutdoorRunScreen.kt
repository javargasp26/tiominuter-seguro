package com.javargasp.tiominutermvi.ui.program.outdoorrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.TimerText
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.program.common.KeepScreenOn
import com.javargasp.tiominutermvi.ui.program.common.ProgramConfirmDialog
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramNotFound
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.ProgramWorkoutFinished
import com.javargasp.tiominutermvi.ui.program.common.currentLocale
import com.javargasp.tiominutermvi.ui.program.common.formatOneDecimal
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.javargasp.tiominutermvi.ui.components.showSessionSaved

/** Outdoor run (or brisk walk) of a program: target distance and stopwatch. */
@Composable
fun ProgramOutdoorRunScreen(
    programId: Long,
    week: Int,
    dayIndex: Int,
    onNavigateBack: () -> Unit,
    viewModel: ProgramOutdoorRunViewModel = koinViewModel { parametersOf(programId, week, dayIndex) }
) {
    KeepScreenOn()
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                ProgramOutdoorRunEffect.NavigateBack -> onNavigateBack()
                is ProgramOutdoorRunEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
                is ProgramOutdoorRunEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
            }
        }
    ) { state, onIntent ->
        BackHandler { onIntent(ProgramOutdoorRunIntent.OnBackPressed) }
        ProgramOutdoorRunContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramOutdoorRunContent(
    state: ProgramOutdoorRunState,
    onIntent: (ProgramOutdoorRunIntent) -> Unit
) {
    val onBack = { onIntent(ProgramOutdoorRunIntent.OnBackPressed) }
    Scaffold(
        topBar = {
            ProgramTopBar(
                title = stringResource(
                    if (state.walk) R.string.program_title_brisk_walk else R.string.program_title_outdoor_run
                ),
                subtitle = stringResource(R.string.program_week_title, state.week),
                onBack = onBack
            )
        }
    ) { padding ->
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isNotFound -> ProgramNotFound(
                text = stringResource(R.string.program_not_found),
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            state.isFinished -> ProgramWorkoutFinished(
                onDone = { onIntent(ProgramOutdoorRunIntent.OnDone) },
                modifier = Modifier.padding(padding),
                photo = state.completionPhoto,
                onPhotoSelected = { onIntent(ProgramOutdoorRunIntent.OnCompletionPhotoSelected(it)) }
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        if (state.walk) R.string.program_walk_target else R.string.program_run_target,
                        formatOneDecimal(state.distanceKm, currentLocale())
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                TimerText(seconds = state.elapsedSeconds, style = MaterialTheme.typography.displayLarge)
                Text(
                    text = stringResource(R.string.program_no_gps_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val (label, intent) = when {
                        !state.isStarted -> stringResource(R.string.program_start) to ProgramOutdoorRunIntent.OnStart
                        state.isRunning -> stringResource(R.string.program_pause) to ProgramOutdoorRunIntent.OnPause
                        else -> stringResource(R.string.program_resume) to ProgramOutdoorRunIntent.OnResume
                    }
                    Button(
                        onClick = { onIntent(intent) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) { Text(label) }
                    OutlinedButton(
                        onClick = { onIntent(ProgramOutdoorRunIntent.OnFinishClicked) },
                        enabled = state.isStarted,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) { Text(stringResource(R.string.program_finish)) }
                }
            }
        }
    }

    if (state.showFinishConfirmation) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_finish_title),
            text = stringResource(R.string.program_finish_text),
            confirmLabel = stringResource(R.string.program_finish),
            dismissLabel = stringResource(R.string.program_cancel),
            onConfirm = { onIntent(ProgramOutdoorRunIntent.OnConfirmFinish) },
            onDismiss = { onIntent(ProgramOutdoorRunIntent.OnDismissFinish) }
        )
    }
    if (state.showExitConfirmation) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_leave_workout_title),
            text = stringResource(R.string.program_leave_workout_text),
            confirmLabel = stringResource(R.string.program_leave),
            dismissLabel = stringResource(R.string.program_stay),
            onConfirm = { onIntent(ProgramOutdoorRunIntent.OnConfirmExit) },
            onDismiss = { onIntent(ProgramOutdoorRunIntent.OnDismissExit) }
        )
    }
}
