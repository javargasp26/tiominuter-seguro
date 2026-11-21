package com.javargasp.tiominutermvi.ui.logactivity

import com.javargasp.tiominutermvi.ui.components.share.ShareIntroDialog
import com.javargasp.tiominutermvi.ui.components.share.ShareSwitchRow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.goal.MIN_COUNTED_DURATION_SECONDS
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryError
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryRules
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoPaths
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.photo.PhotoPrivacyNote
import com.javargasp.tiominutermvi.ui.components.photo.TrainingPhotoChooser
import com.javargasp.tiominutermvi.ui.components.photo.TrainingPhotoThumbnail
import com.javargasp.tiominutermvi.ui.components.photo.TrainingPhotoViewer
import com.javargasp.tiominutermvi.ui.components.photo.rememberTrainingPhotoFiles
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.traininglog.DayLabel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Log an activity done outside the app's routines, or edit one ([entryId] not null).
 * Save validates and goes back; edit mode also offers Delete. Back with unsaved changes
 * asks for confirmation.
 */
@Composable
fun LogActivityScreen(
    entryId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: LogActivityViewModel = koinViewModel(key = "log_activity_${entryId ?: "new"}") {
        if (entryId == null) parametersOf() else parametersOf(entryId)
    }
) {
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is LogActivityEffect.NavigateBack -> onNavigateBack()
                is LogActivityEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
            }
        }
    ) { state, onIntent ->
        BackHandler { onIntent(LogActivityIntent.OnBackPressed) }
        LogActivityContent(state = state, onIntent = onIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogActivityContent(
    state: LogActivityState,
    onIntent: (LogActivityIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditMode) R.string.log_activity_title_edit else R.string.log_activity_title_new
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(LogActivityIntent.OnBackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (state.isEditMode && state.form != null) {
                        IconButton(onClick = { onIntent(LogActivityIntent.OnDeleteClicked) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.log_activity_delete)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val form = state.form
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.isNotFound || form == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.log_activity_not_found),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            else -> LogActivityFormContent(
                state = state,
                form = form,
                onIntent = onIntent,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    if (state.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { onIntent(LogActivityIntent.OnDismissDiscard) },
            title = { Text(stringResource(R.string.log_activity_discard_title)) },
            text = { Text(stringResource(R.string.log_activity_discard_message)) },
            confirmButton = {
                TextButton(onClick = { onIntent(LogActivityIntent.OnConfirmDiscard) }) {
                    Text(stringResource(R.string.log_activity_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(LogActivityIntent.OnDismissDiscard) }) {
                    Text(stringResource(R.string.log_activity_keep_editing))
                }
            }
        )
    }

    if (state.showShareIntro) {
        ShareIntroDialog(
            onOk = { onIntent(LogActivityIntent.OnShareIntroOk) },
            onDontShare = { onIntent(LogActivityIntent.OnShareIntroDontShare) },
            onDismiss = { onIntent(LogActivityIntent.OnShareIntroDismissed) }
        )
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { onIntent(LogActivityIntent.OnDismissDelete) },
            title = { Text(stringResource(R.string.log_activity_delete_title)) },
            text = { Text(stringResource(R.string.log_activity_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onIntent(LogActivityIntent.OnConfirmDelete) }) {
                    Text(stringResource(R.string.log_activity_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(LogActivityIntent.OnDismissDelete) }) {
                    Text(stringResource(R.string.log_activity_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogActivityFormContent(
    state: LogActivityState,
    form: LogActivityForm,
    onIntent: (LogActivityIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val errors = state.errors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Activity type
        SectionTitle(stringResource(R.string.log_activity_type))
        ActivityTypeChips(selected = form.type, onSelect = { onIntent(LogActivityIntent.OnTypeSelected(it)) })
        if (ActivityEntryError.TYPE_MISSING in errors) ErrorText(stringResource(R.string.log_activity_error_type))
        if (form.type == ActivityType.OTHER) {
            OutlinedTextField(
                value = form.customName,
                onValueChange = { onIntent(LogActivityIntent.OnCustomNameChanged(it)) },
                label = { Text(stringResource(R.string.log_activity_custom_name)) },
                singleLine = true,
                isError = ActivityEntryError.NAME_MISSING in errors || ActivityEntryError.NAME_TOO_LONG in errors,
                supportingText = {
                    Text(
                        stringResource(
                            R.string.log_activity_counter,
                            form.customName.length,
                            ActivityEntryRules.NAME_MAX_LENGTH
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (ActivityEntryError.NAME_MISSING in errors) ErrorText(stringResource(R.string.log_activity_error_name))
            if (ActivityEntryError.NAME_TOO_LONG in errors) {
                ErrorText(tooLongText(ActivityEntryRules.NAME_MAX_LENGTH))
            }
        }

        // Duration
        SectionTitle(stringResource(R.string.log_activity_duration))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogActivityState.QUICK_DURATIONS.forEach { minutes ->
                FilterChip(
                    selected = form.durationMinutes == minutes,
                    onClick = { onIntent(LogActivityIntent.OnQuickDurationSelected(minutes)) },
                    label = { Text(pluralStringResource(R.plurals.log_activity_minutes_short, minutes, minutes)) }
                )
            }
        }
        OutlinedTextField(
            value = form.durationText,
            onValueChange = { onIntent(LogActivityIntent.OnDurationChanged(it)) },
            label = { Text(stringResource(R.string.log_activity_minutes_field)) },
            singleLine = true,
            isError = ActivityEntryError.DURATION_OUT_OF_RANGE in errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (ActivityEntryError.DURATION_OUT_OF_RANGE in errors) {
            val max = ActivityEntryRules.DURATION_MINUTES.last
            ErrorText(
                pluralStringResource(
                    R.plurals.log_activity_error_duration,
                    max,
                    ActivityEntryRules.DURATION_MINUTES.first,
                    max
                )
            )
        }
        if (state.showsShortDurationHint) {
            val minimum = MIN_COUNTED_DURATION_SECONDS / LogActivityState.SECONDS_PER_MINUTE
            Text(
                text = pluralStringResource(R.plurals.log_activity_short_duration_hint, minimum, minimum),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }

        // Intensity
        SectionTitle(stringResource(R.string.log_activity_intensity))
        IntensitySelector(selected = form.intensity, onSelect = { onIntent(LogActivityIntent.OnIntensitySelected(it)) })
        if (ActivityEntryError.INTENSITY_MISSING in errors) {
            ErrorText(stringResource(R.string.log_activity_error_intensity))
        }

        // Date and time
        SectionTitle(stringResource(R.string.log_activity_when))
        DayChips(options = state.dayOptions, selected = form.date, onSelect = { onIntent(LogActivityIntent.OnDateSelected(it)) })
        TimeField(hour = form.hour, minute = form.minute, onTimeSelected = { h, m -> onIntent(LogActivityIntent.OnTimeSelected(h, m)) })
        if (ActivityEntryError.DATE_IN_FUTURE in errors) ErrorText(stringResource(R.string.log_activity_error_future))
        if (ActivityEntryError.DATE_TOO_EARLY in errors) {
            val days = ActivityEntryRules.DAYS_BACK + 1
            ErrorText(pluralStringResource(R.plurals.log_activity_error_too_early, days, days))
        }

        // Note
        OutlinedTextField(
            value = form.note,
            onValueChange = { onIntent(LogActivityIntent.OnNoteChanged(it)) },
            label = { Text(stringResource(R.string.log_activity_note)) },
            minLines = 3,
            isError = ActivityEntryError.NOTE_TOO_LONG in errors,
            supportingText = {
                Text(stringResource(R.string.log_activity_counter, form.note.length, ActivityEntryRules.NOTE_MAX_LENGTH))
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (ActivityEntryError.NOTE_TOO_LONG in errors) ErrorText(tooLongText(ActivityEntryRules.NOTE_MAX_LENGTH))

        // Photo (optional)
        PhotoField(
            photo = form.photo,
            date = form.date,
            isProcessing = state.isProcessingPhoto,
            onPhotoSelected = { onIntent(LogActivityIntent.OnPhotoSelected(it)) },
            onRemove = { onIntent(LogActivityIntent.OnRemovePhoto) }
        )

        if (state.canShare) {
            ShareSwitchRow(
                checked = state.shareInGroups,
                onCheckedChange = { onIntent(LogActivityIntent.OnShareChanged(it)) }
            )
        }

        if (state.saveFailed) ErrorText(stringResource(R.string.log_activity_save_failed))
        Button(
            onClick = { onIntent(LogActivityIntent.OnSave) },
            enabled = !state.isSaving && !state.isProcessingPhoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.log_activity_save))
        }
    }
}

/** One chip per activity type; the icon is decorative, the label is the text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityTypeChips(selected: ActivityType?, onSelect: (ActivityType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                leadingIcon = {
                    Text(text = activityTypeIcon(type), modifier = Modifier.clearAndSetSemantics { })
                },
                label = { Text(stringResource(activityTypeLabel(type))) }
            )
        }
    }
}

/** Easy / Moderate / Hard, with the description of the selected one below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntensitySelector(selected: Intensity?, onSelect: (Intensity) -> Unit) {
    val options = Intensity.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, intensity ->
            val description = stringResource(
                R.string.log_activity_intensity_option,
                stringResource(intensityLabel(intensity)),
                stringResource(intensityDescription(intensity))
            )
            SegmentedButton(
                selected = intensity == selected,
                onClick = { onSelect(intensity) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = description }
            ) { Text(stringResource(intensityLabel(intensity)), maxLines = 1) }
        }
    }
    options.forEach { intensity ->
        Text(
            text = stringResource(
                R.string.log_activity_intensity_option,
                stringResource(intensityLabel(intensity)),
                stringResource(intensityDescription(intensity))
            ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (intensity == selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (intensity == selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            // Already read on each segment
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

/** Today, Yesterday and the previous days, scrollable horizontally. */
@Composable
private fun DayChips(options: List<DayOption>, selected: String, onSelect: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.date == selected,
                onClick = { onSelect(option.date) },
                label = {
                    Text(
                        when (option.label) {
                            DayLabel.Today -> stringResource(R.string.training_history_today)
                            DayLabel.Yesterday -> stringResource(R.string.training_history_yesterday)
                            is DayLabel.Date -> formatDay(option.date, locale)
                        }
                    )
                }
            )
        }
    }
}

/** The chosen time (device 12/24-hour format); tapping it opens a time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(hour: Int, minute: Int, onTimeSelected: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val timeText = formatTime(hour, minute, context)
    val changeLabel = stringResource(R.string.log_activity_time_change, timeText)
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = changeLabel }
    ) {
        Text(stringResource(R.string.log_activity_time, timeText))
    }
    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    onTimeSelected(pickerState.hour, pickerState.minute)
                }) { Text(stringResource(R.string.log_activity_time_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.log_activity_cancel)) }
            }
        )
    }
}

/**
 * The optional photo: a thumbnail (tap: full screen) with Change / Remove, or Add photo;
 * a progress line while a picked photo is processed; the privacy note.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoField(
    photo: FormPhoto,
    date: String,
    isProcessing: Boolean,
    onPhotoSelected: (TrainingPhotoSource) -> Unit,
    onRemove: () -> Unit
) {
    var showChooser by rememberSaveable { mutableStateOf(false) }
    var showViewer by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val description = stringResource(R.string.photo_content_description, formatDay(date, locale))
    val files = rememberTrainingPhotoFiles()
    val thumbnail = when (photo) {
        FormPhoto.None -> null
        is FormPhoto.Stored -> files.fileOf(TrainingPhotoPaths.thumbnailPath(photo.photoPath))
        is FormPhoto.Staged -> photo.staged.thumbnailFile
    }

    SectionTitle(stringResource(R.string.log_activity_photo))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        if (thumbnail != null) {
            TrainingPhotoThumbnail(file = thumbnail, contentDescription = description, onClick = { showViewer = true })
        }
        OutlinedButton(
            onClick = { showChooser = true },
            enabled = !isProcessing,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(stringResource(if (thumbnail == null) R.string.photo_add else R.string.photo_change))
        }
        if (thumbnail != null) {
            TextButton(onClick = onRemove, enabled = !isProcessing, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.photo_remove))
            }
        }
    }
    if (isProcessing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.photo_processing), style = MaterialTheme.typography.bodySmall)
        }
    }
    PhotoPrivacyNote()

    TrainingPhotoChooser(visible = showChooser, onDismiss = { showChooser = false }, onPhotoSelected = onPhotoSelected)
    val full = when (photo) {
        FormPhoto.None -> null
        is FormPhoto.Stored -> files.fileOf(photo.photoPath)
        is FormPhoto.Staged -> photo.staged.photoFile
    }
    if (showViewer && full != null) {
        TrainingPhotoViewer(file = full, contentDescription = description, onDismiss = { showViewer = false })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
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

@Composable
private fun tooLongText(max: Int): String = pluralStringResource(R.plurals.log_activity_error_too_long, max, max)

/** "Mon, 13 Oct" / "lun, 13 oct", in the locale's own order. */
private fun formatDay(date: String, locale: Locale): String {
    val parsed = SimpleDateFormat(LocalDateProvider.DATE_PATTERN, Locale.ROOT).parse(date) ?: return date
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
    // Parsed and formatted in the same (default) zone, so the calendar date is kept
    return SimpleDateFormat(pattern, locale).format(parsed)
}

private fun formatTime(hour: Int, minute: Int, context: android.content.Context): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
