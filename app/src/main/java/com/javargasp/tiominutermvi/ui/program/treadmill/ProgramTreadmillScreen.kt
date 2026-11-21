package com.javargasp.tiominutermvi.ui.program.treadmill

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.TreadmillSegment
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.TimerText
import com.javargasp.tiominutermvi.ui.components.formatSeconds
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.program.common.KeepScreenOn
import com.javargasp.tiominutermvi.ui.program.common.ProgramConfirmDialog
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramNotFound
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.ProgramWorkoutFinished
import com.javargasp.tiominutermvi.ui.program.common.currentLocale
import com.javargasp.tiominutermvi.ui.program.common.formatOneDecimal
import com.javargasp.tiominutermvi.ui.program.common.treadmillSegmentLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.javargasp.tiominutermvi.ui.components.showSessionSaved

/** Treadmill session of a program. Keeps the screen on while visible. */
@Composable
fun ProgramTreadmillScreen(
    programId: Long,
    week: Int,
    dayIndex: Int,
    onNavigateBack: () -> Unit,
    viewModel: ProgramTreadmillViewModel = koinViewModel { parametersOf(programId, week, dayIndex) }
) {
    KeepScreenOn()
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                ProgramTreadmillEffect.NavigateBack -> onNavigateBack()
                is ProgramTreadmillEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
                is ProgramTreadmillEffect.ShowSessionSaved -> showSessionSaved(context, effect.durationSeconds)
            }
        }
    ) { state, onIntent ->
        BackHandler { onIntent(ProgramTreadmillIntent.OnBackPressed) }
        ProgramTreadmillContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramTreadmillContent(
    state: ProgramTreadmillState,
    onIntent: (ProgramTreadmillIntent) -> Unit
) {
    val onBack = { onIntent(ProgramTreadmillIntent.OnBackPressed) }
    Scaffold(
        topBar = {
            ProgramTopBar(
                title = stringResource(R.string.program_title_treadmill),
                subtitle = stringResource(R.string.program_week_title, state.week),
                onBack = onBack
            )
        }
    ) { padding ->
        val current = state.currentSegment
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isNotFound || current == null -> ProgramNotFound(
                text = stringResource(R.string.program_not_found),
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            state.isFinished -> ProgramWorkoutFinished(
                onDone = { onIntent(ProgramTreadmillIntent.OnDone) },
                modifier = Modifier.padding(padding),
                photo = state.completionPhoto,
                onPhotoSelected = { onIntent(ProgramTreadmillIntent.OnCompletionPhotoSelected(it)) }
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.program_segment_of, state.currentIndex + 1, state.segments.size),
                    style = MaterialTheme.typography.labelLarge
                )
                CurrentSegment(current, state.remainingSeconds)
                state.nextSegment?.let { next ->
                    Text(
                        text = stringResource(R.string.program_next, segmentLine(next)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } ?: Text(text = stringResource(R.string.program_last_segment), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.program_total_remaining, formatSeconds(state.totalRemainingSeconds)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Controls(state, onIntent)
                HorizontalDivider()
                SegmentList(state)
            }
        }
    }

    if (state.showExitConfirmation) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_leave_workout_title),
            text = stringResource(R.string.program_leave_workout_text),
            confirmLabel = stringResource(R.string.program_leave),
            dismissLabel = stringResource(R.string.program_stay),
            onConfirm = { onIntent(ProgramTreadmillIntent.OnConfirmExit) },
            onDismiss = { onIntent(ProgramTreadmillIntent.OnDismissExit) }
        )
    }
}

@Composable
private fun CurrentSegment(segment: TreadmillSegment, remainingSeconds: Int) {
    val locale = currentLocale()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = treadmillSegmentLabel(segment.label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            TimerText(seconds = remainingSeconds, style = MaterialTheme.typography.displayLarge)
            Text(
                text = stringResource(R.string.program_speed, formatOneDecimal(segment.speedKmh, locale)),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.program_incline, formatOneDecimal(segment.inclinePercent, locale)),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun Controls(state: ProgramTreadmillState, onIntent: (ProgramTreadmillIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val (label, intent) = when {
            !state.isStarted -> stringResource(R.string.program_start) to ProgramTreadmillIntent.OnStart
            state.isRunning -> stringResource(R.string.program_pause) to ProgramTreadmillIntent.OnPause
            else -> stringResource(R.string.program_resume) to ProgramTreadmillIntent.OnResume
        }
        Button(
            onClick = { onIntent(intent) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        ) { Text(label) }
        OutlinedButton(
            onClick = { onIntent(ProgramTreadmillIntent.OnSkipSegment) },
            enabled = state.isStarted,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        ) { Text(stringResource(R.string.program_skip_segment)) }
    }
}

@Composable
private fun SegmentList(state: ProgramTreadmillState) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.segments.forEachIndexed { index, segment ->
            val style = if (index == state.currentIndex) {
                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            }
            val color = if (index < state.currentIndex) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Text(
                text = "${index + 1}. ${segmentLine(segment)} · ${formatSeconds(segment.durationSeconds)}",
                style = style,
                color = color
            )
        }
    }
}

/** "Jog · 8.5 km/h · 2.0 %". */
@Composable
private fun segmentLine(segment: TreadmillSegment): String {
    val locale = currentLocale()
    return stringResource(
        R.string.program_segment_line,
        treadmillSegmentLabel(segment.label),
        formatOneDecimal(segment.speedKmh, locale),
        formatOneDecimal(segment.inclinePercent, locale)
    )
}
