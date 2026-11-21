package com.javargasp.tiominutermvi.ui.program.strengthexercise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.ProgramSlotType
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.ExerciseAnimation
import com.javargasp.tiominutermvi.ui.components.REST_ANIMATION
import com.javargasp.tiominutermvi.ui.components.TimerText
import com.javargasp.tiominutermvi.ui.program.common.KeepScreenOn
import com.javargasp.tiominutermvi.ui.program.common.ProgramCircularCountdown
import com.javargasp.tiominutermvi.ui.program.common.ProgramConfirmDialog
import com.javargasp.tiominutermvi.ui.program.common.ProgramLoading
import com.javargasp.tiominutermvi.ui.program.common.ProgramNotFound
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.isSpanish
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Sets of one exercise of a strength workout. Keeps the screen on while visible. */
@Composable
fun ProgramStrengthExerciseScreen(
    programId: Long,
    week: Int,
    dayIndex: Int,
    position: Int,
    onNavigateBack: () -> Unit,
    viewModel: ProgramStrengthExerciseViewModel = koinViewModel {
        parametersOf(programId, week, dayIndex, position)
    }
) {
    KeepScreenOn()
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                ProgramStrengthExerciseEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        BackHandler { onIntent(ProgramStrengthExerciseIntent.OnBackPressed) }
        ProgramStrengthExerciseContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramStrengthExerciseContent(
    state: ProgramStrengthExerciseState,
    onIntent: (ProgramStrengthExerciseIntent) -> Unit
) {
    val unknown = stringResource(R.string.program_unknown_exercise)
    val name = state.exercise?.displayName(isSpanish()) ?: if (state.slot != null) unknown else ""
    val onBack = { onIntent(ProgramStrengthExerciseIntent.OnBackPressed) }
    Scaffold(
        topBar = { ProgramTopBar(title = name, onBack = onBack) }
    ) { padding ->
        val phase = state.phase
        when {
            state.isLoading -> ProgramLoading(Modifier.padding(padding))
            state.isNotFound || phase == null -> ProgramNotFound(
                text = stringResource(R.string.program_not_found),
                onBack = onBack,
                modifier = Modifier.padding(padding)
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.program_exercise_of, state.exerciseNumber, state.totalExercises),
                    style = MaterialTheme.typography.labelLarge
                )
                val resting = phase is ProgramExercisePhase.Resting
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExerciseAnimation(
                        animation = if (resting) REST_ANIMATION else state.exercise?.animation.orEmpty(),
                        contentDescription = if (resting) stringResource(R.string.program_rest) else name,
                        modifier = Modifier.size(220.dp)
                    )
                }
                PhaseContent(state, phase, onIntent)
            }
        }
    }

    if (state.showExitConfirmation) {
        ProgramConfirmDialog(
            title = stringResource(R.string.program_exit_exercise_title),
            text = pluralStringResource(
                R.plurals.program_exit_sets_text,
                state.totalSets,
                state.completedSets,
                state.totalSets
            ),
            confirmLabel = stringResource(R.string.program_leave),
            dismissLabel = stringResource(R.string.program_stay),
            onConfirm = { onIntent(ProgramStrengthExerciseIntent.OnConfirmExit) },
            onDismiss = { onIntent(ProgramStrengthExerciseIntent.OnDismissExit) }
        )
    }
}

@Composable
private fun PhaseContent(
    state: ProgramStrengthExerciseState,
    phase: ProgramExercisePhase,
    onIntent: (ProgramStrengthExerciseIntent) -> Unit
) {
    val slot = state.slot ?: return
    val setIndex = when (phase) {
        is ProgramExercisePhase.Resting -> phase.nextSetIndex - 1
        else -> state.completedSets
    }
    Text(
        text = stringResource(R.string.program_set_of, setIndex + 1, state.totalSets),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
    when (phase) {
        is ProgramExercisePhase.PerformingReps -> {
            val reps = slot.reps[phase.setIndex]
            Text(
                text = pluralStringResource(repsPlural(slot.perSide), reps, reps),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center
            )
            PrimaryButton(stringResource(R.string.program_set_done)) { onIntent(ProgramStrengthExerciseIntent.OnSetDone) }
        }
        is ProgramExercisePhase.ReadyToStartTimed -> {
            TimerText(seconds = slot.reps[phase.setIndex], style = MaterialTheme.typography.displayMedium)
            if (slot.perSide) {
                Text(
                    text = stringResource(R.string.program_per_side_order),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            PrimaryButton(stringResource(R.string.program_start)) { onIntent(ProgramStrengthExerciseIntent.OnStartTimedSet) }
        }
        is ProgramExercisePhase.GetReady -> {
            Text(text = stringResource(R.string.program_get_ready), style = MaterialTheme.typography.titleMedium)
            TimerText(seconds = phase.remainingSeconds, style = MaterialTheme.typography.displayMedium)
        }
        is ProgramExercisePhase.PerformingTimed -> {
            phase.side?.let { side ->
                Text(
                    text = stringResource(sideLabel(side)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            ProgramCircularCountdown(remainingSeconds = phase.remainingSeconds, totalSeconds = phase.totalSeconds)
        }
        is ProgramExercisePhase.SwitchingSides -> {
            Text(text = stringResource(R.string.program_switch_sides), style = MaterialTheme.typography.titleMedium)
            TimerText(seconds = phase.remainingSeconds, style = MaterialTheme.typography.displayMedium)
            Text(
                text = stringResource(R.string.program_next, stringResource(R.string.program_right_side)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        is ProgramExercisePhase.Resting -> {
            Text(text = stringResource(R.string.program_rest), style = MaterialTheme.typography.titleMedium)
            ProgramCircularCountdown(remainingSeconds = phase.remainingSeconds, totalSeconds = phase.totalSeconds)
            Text(
                text = stringResource(R.string.program_next_set, phase.nextSetIndex + 1),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick = { onIntent(ProgramStrengthExerciseIntent.OnSkipRest) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.program_skip_rest)) }
        }
    }
    if (slot.type == ProgramSlotType.TIME && phase is ProgramExercisePhase.PerformingTimed) {
        Text(text = stringResource(R.string.program_hold), style = MaterialTheme.typography.bodyMedium)
    }
}

/** "10 reps" or, for a per-side slot, "10 reps per side". */
internal fun repsPlural(perSide: Boolean): Int =
    if (perSide) R.plurals.program_reps_per_side else R.plurals.program_reps

private fun sideLabel(side: ProgramSide): Int = when (side) {
    ProgramSide.LEFT -> R.string.program_left_side
    ProgramSide.RIGHT -> R.string.program_right_side
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}
