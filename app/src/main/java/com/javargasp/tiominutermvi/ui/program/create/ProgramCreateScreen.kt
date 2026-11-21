package com.javargasp.tiominutermvi.ui.program.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.program.model.ProgramExercise
import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.model.ProgramWorkout
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.program.common.ProgramTopBar
import com.javargasp.tiominutermvi.ui.program.common.currentLocale
import com.javargasp.tiominutermvi.ui.program.common.focusDescription
import com.javargasp.tiominutermvi.ui.program.common.focusLabel
import com.javargasp.tiominutermvi.ui.program.common.formatOneDecimal
import com.javargasp.tiominutermvi.ui.program.common.goalLabel
import com.javargasp.tiominutermvi.ui.program.common.isSpanish
import com.javargasp.tiominutermvi.ui.program.common.levelLabel
import com.javargasp.tiominutermvi.ui.program.common.locationLabel
import com.javargasp.tiominutermvi.ui.program.common.workoutTitle
import com.javargasp.tiominutermvi.ui.program.common.workoutSummary
import org.koin.androidx.compose.koinViewModel

/** Form that generates a program from the user's profile. */
@Composable
fun ProgramCreateScreen(
    onNavigateToDetail: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProgramCreateViewModel = koinViewModel()
) {
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is ProgramCreateEffect.NavigateToDetail -> onNavigateToDetail(effect.programId)
                ProgramCreateEffect.NavigateBack -> onNavigateBack()
            }
        }
    ) { state, onIntent ->
        ProgramCreateContent(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun ProgramCreateContent(
    state: ProgramCreateState,
    onIntent: (ProgramCreateIntent) -> Unit
) {
    Scaffold(
        topBar = {
            ProgramTopBar(
                title = stringResource(R.string.program_create_title),
                onBack = { onIntent(ProgramCreateIntent.OnBackPressed) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NumberField(
                value = state.ageText,
                label = stringResource(R.string.program_age_label),
                error = state.ageError,
                range = ProgramProfile.AGE_RANGE,
                keyboardType = KeyboardType.Number,
                onValueChange = { onIntent(ProgramCreateIntent.OnAgeChanged(it)) }
            )
            NumberField(
                value = state.weightText,
                label = stringResource(R.string.program_weight_label),
                error = state.weightError,
                range = ProgramProfile.WEIGHT_RANGE_KG.start.toInt()..ProgramProfile.WEIGHT_RANGE_KG.endInclusive.toInt(),
                keyboardType = KeyboardType.Decimal,
                onValueChange = { onIntent(ProgramCreateIntent.OnWeightChanged(it)) }
            )
            NumberField(
                value = state.heightText,
                label = stringResource(R.string.program_height_label),
                error = state.heightError,
                range = ProgramProfile.HEIGHT_RANGE_CM,
                keyboardType = KeyboardType.Number,
                onValueChange = { onIntent(ProgramCreateIntent.OnHeightChanged(it)) }
            )

            BmiCard(state)

            ChoiceRow(
                title = stringResource(R.string.program_level_label),
                options = ProgramLevel.entries,
                selected = state.level,
                label = { levelLabel(it) },
                onSelect = { onIntent(ProgramCreateIntent.OnLevelSelected(it)) }
            )
            ChoiceRow(
                title = stringResource(R.string.program_goal_label),
                options = ProgramGoal.entries,
                selected = state.goal,
                label = { goalLabel(it) },
                onSelect = { onIntent(ProgramCreateIntent.OnGoalSelected(it)) }
            )
            FocusSelector(
                selected = state.focus,
                onSelect = { onIntent(ProgramCreateIntent.OnFocusSelected(it)) }
            )
            ChoiceRow(
                title = stringResource(R.string.program_location_label),
                options = ProgramLocation.entries,
                selected = state.location,
                label = { locationLabel(it) },
                onSelect = { onIntent(ProgramCreateIntent.OnLocationSelected(it)) }
            )
            Stepper(
                title = stringResource(R.string.program_weeks_label),
                value = state.weeks,
                range = ProgramProfile.WEEKS_RANGE,
                onChange = { onIntent(ProgramCreateIntent.OnWeeksChanged(it)) }
            )
            Stepper(
                title = stringResource(R.string.program_days_label),
                value = state.daysPerWeek,
                range = ProgramProfile.DAYS_PER_WEEK_RANGE,
                onChange = { onIntent(ProgramCreateIntent.OnDaysPerWeekChanged(it)) }
            )

            Text(
                text = stringResource(R.string.program_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.createFailed) {
                Text(
                    text = stringResource(R.string.program_create_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onIntent(ProgramCreateIntent.OnPreviewClicked) },
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.program_preview_button)) }
                Button(
                    onClick = { onIntent(ProgramCreateIntent.OnCreateClicked) },
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.program_create_button)) }
            }
        }
    }

    state.preview?.let { workouts ->
        PreviewDialog(
            focus = state.focus,
            workouts = workouts,
            exercises = state.exercises,
            onDismiss = { onIntent(ProgramCreateIntent.OnDismissPreview) }
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    error: ProgramFieldError?,
    range: IntRange,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            val text = when (error) {
                ProgramFieldError.REQUIRED -> stringResource(R.string.program_error_required)
                ProgramFieldError.OUT_OF_RANGE -> stringResource(R.string.program_error_range, range.first, range.last)
                null -> stringResource(R.string.program_hint_range, range.first, range.last)
            }
            Text(text)
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun BmiCard(state: ProgramCreateState) {
    val bmi = state.bmi ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.program_bmi, formatOneDecimal(bmi, currentLocale())),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (state.isLowImpact) {
                Text(stringResource(R.string.program_low_impact_note), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.showUnderweightWarning) {
                Text(
                    text = stringResource(R.string.program_underweight_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(label(option), maxLines = 1) }
            }
        }
    }
}

/**
 * The four focus options as a radio group: label and one-line description per option.
 * Each row is one selectable target (at least 48dp) whose selected state TalkBack reads.
 */
@Composable
private fun FocusSelector(selected: ProgramFocus, onSelect: (ProgramFocus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.program_focus_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Column(modifier = Modifier.selectableGroup()) {
            ProgramFocus.entries.forEach { focus ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = focus == selected,
                            onClick = { onSelect(focus) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // The row handles the click; the radio button only shows the state.
                    RadioButton(selected = focus == selected, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = focusLabel(focus), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = focusDescription(focus),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val decrease = stringResource(R.string.program_decrease, title)
    val increase = stringResource(R.string.program_increase, title)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = decrease }
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.widthIn(min = 32.dp)
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = increase }
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PreviewDialog(
    focus: ProgramFocus,
    workouts: List<ProgramWorkout>,
    exercises: Map<Int, ProgramExercise>,
    onDismiss: () -> Unit
) {
    val spanish = isSpanish()
    val unknown = stringResource(R.string.program_unknown_exercise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.program_week_title, 1)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.program_focus_value, focusLabel(focus)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                workouts.forEach { workout ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = workoutTitle(workout), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        val lines = when (workout) {
                            is ProgramWorkout.Strength -> workout.slots.map { slot ->
                                exercises[slot.exerciseId]?.displayName(spanish) ?: unknown
                            }
                            is ProgramWorkout.HomeCircuit -> listOf(workoutSummary(workout)) +
                                workout.exerciseIds.map { exercises[it]?.displayName(spanish) ?: unknown }
                            else -> listOf(workoutSummary(workout))
                        }
                        lines.forEach { Text(text = "• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.program_close)) } }
    )
}
