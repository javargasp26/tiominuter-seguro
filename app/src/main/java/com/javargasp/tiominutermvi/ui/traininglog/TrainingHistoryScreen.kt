package com.javargasp.tiominutermvi.ui.traininglog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javargasp.tiominutermvi.R
import com.javargasp.tiominutermvi.domain.goal.countsTowardGoal
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingSource
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalog
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.base.MviComposable
import com.javargasp.tiominutermvi.ui.components.photo.TrainingPhotoChooser
import com.javargasp.tiominutermvi.ui.components.photo.TrainingPhotoThumbnail
import com.javargasp.tiominutermvi.ui.components.photo.TrainingPhotoViewer
import com.javargasp.tiominutermvi.ui.components.photo.photoContentDescription
import com.javargasp.tiominutermvi.ui.components.photo.rememberPhotoFile
import com.javargasp.tiominutermvi.ui.components.photo.rememberThumbnailFile
import com.javargasp.tiominutermvi.ui.components.photo.showTrainingPhotoError
import com.javargasp.tiominutermvi.ui.logactivity.activityTypeIcon
import com.javargasp.tiominutermvi.ui.components.sourceLabelRes
import com.javargasp.tiominutermvi.ui.components.text
import com.javargasp.tiominutermvi.ui.logactivity.intensityLabel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Training history: every completed workout (daily, gym, program, logged activities),
 * grouped by week and day. Tapping a logged activity opens it for editing; a long press on
 * it (or its accessibility action) asks to delete it. A long press on an automatic entry
 * opens a menu: add, change or remove its photo, or delete it (also accessibility actions).
 * Entries with a photo show a thumbnail that opens it full screen. The FAB logs a new activity.
 */
@Composable
fun TrainingHistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogActivity: () -> Unit,
    onNavigateToEditActivity: (Long) -> Unit,
    viewModel: TrainingHistoryViewModel = koinViewModel()
) {
    val context = LocalContext.current
    MviComposable(
        viewModel = viewModel,
        onEffect = { effect ->
            when (effect) {
                is TrainingHistoryEffect.NavigateBack -> onNavigateBack()
                is TrainingHistoryEffect.NavigateToLogActivity -> onNavigateToLogActivity()
                is TrainingHistoryEffect.NavigateToEditActivity -> onNavigateToEditActivity(effect.entryId)
                is TrainingHistoryEffect.ShowPhotoError -> showTrainingPhotoError(context, effect.error)
            }
        }
    ) { state, onIntent ->
        TrainingHistoryContent(state = state, onIntent = onIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrainingHistoryContent(
    state: TrainingHistoryState,
    onIntent: (TrainingHistoryIntent) -> Unit
) {
    // The entry whose photo is being chosen (kept while the camera or gallery is open), the
    // source sheet and the entry whose photo is shown full screen: UI-only state
    var photoTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }
    var viewedPhotoEntryId by rememberSaveable { mutableStateOf<Long?>(null) }
    val actions = HistoryRowActions(
        onClick = { onIntent(TrainingHistoryIntent.OnEntryClicked(it)) },
        onDelete = { onIntent(TrainingHistoryIntent.OnDeleteRequested(it)) },
        onAddPhoto = {
            photoTargetId = it
            showPhotoSheet = true
        },
        onRemovePhoto = { onIntent(TrainingHistoryIntent.OnRemovePhotoRequested(it)) },
        onViewPhoto = { viewedPhotoEntryId = it }
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.training_history_title)) },
                navigationIcon = {
                    IconButton(onClick = { onIntent(TrainingHistoryIntent.OnBackPressed) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(TrainingHistoryIntent.OnLogActivityClicked) }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.log_activity_button))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.isEmpty -> Text(
                    text = stringResource(R.string.training_history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
                else -> HistoryList(
                    weeks = state.weeks,
                    names = state.names,
                    processingPhotoEntryIds = state.processingPhotoEntryIds,
                    actions = actions
                )
            }
        }
    }

    state.pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { onIntent(TrainingHistoryIntent.OnDismissDelete) },
            title = { Text(stringResource(R.string.training_history_delete_title)) },
            text = { Text(stringResource(R.string.training_history_delete_message, state.nameOf(entry).text())) },
            confirmButton = {
                TextButton(onClick = { onIntent(TrainingHistoryIntent.OnConfirmDelete) }) {
                    Text(stringResource(R.string.training_history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(TrainingHistoryIntent.OnDismissDelete) }) {
                    Text(stringResource(R.string.training_history_delete_cancel))
                }
            }
        )
    }

    if (state.pendingPhotoRemovalEntry != null) {
        AlertDialog(
            onDismissRequest = { onIntent(TrainingHistoryIntent.OnDismissRemovePhoto) },
            title = { Text(stringResource(R.string.photo_remove_title)) },
            text = { Text(stringResource(R.string.photo_remove_message)) },
            confirmButton = {
                TextButton(onClick = { onIntent(TrainingHistoryIntent.OnConfirmRemovePhoto) }) {
                    Text(stringResource(R.string.photo_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(TrainingHistoryIntent.OnDismissRemovePhoto) }) {
                    Text(stringResource(R.string.photo_remove_cancel))
                }
            }
        )
    }

    TrainingPhotoChooser(
        visible = showPhotoSheet,
        onDismiss = { showPhotoSheet = false },
        onPhotoSelected = { source ->
            photoTargetId?.let { onIntent(TrainingHistoryIntent.OnPhotoSelected(it, source)) }
            photoTargetId = null
        }
    )

    val viewed = viewedPhotoEntryId?.let(state::entry)
    val viewedPath = viewed?.photoPath
    if (viewed != null && viewedPath != null) {
        TrainingPhotoViewer(
            file = rememberPhotoFile(viewedPath),
            contentDescription = photoContentDescription(viewed.completedAt),
            onDismiss = { viewedPhotoEntryId = null }
        )
    }
}

/** What a history row can do; each callback takes the entry id. */
private class HistoryRowActions(
    val onClick: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onAddPhoto: (Long) -> Unit,
    val onRemovePhoto: (Long) -> Unit,
    val onViewPhoto: (Long) -> Unit
)

@Composable
private fun HistoryList(
    weeks: List<TrainingWeek>,
    names: WorkoutNameCatalog,
    processingPhotoEntryIds: Set<Long>,
    actions: HistoryRowActions
) {
    LazyColumn(
        // Bottom room so the FAB doesn't cover the last entry
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weeks.forEach { week ->
            item(key = "week-${week.weekStart}") {
                WeekHeader(week)
            }
            week.days.forEach { day ->
                dayItems(day, names, processingPhotoEntryIds, actions)
            }
        }
    }
}

/** "Week of 13 October · 3 of 4 workouts ✓" (the goal in force that week), or "· 3 workouts" without one. */
@Composable
private fun WeekHeader(week: TrainingWeek) {
    val locale = LocalConfiguration.current.locales[0]
    val weekOf = stringResource(R.string.training_history_week_of, formatDate(week.weekStart, locale, "dMMMM"))
    val progress = week.progress
    val goal = progress.goal
    val count = if (goal != null) {
        pluralStringResource(R.plurals.weekly_goal_progress, goal, progress.workoutCount, goal)
    } else {
        pluralStringResource(R.plurals.training_history_week_workouts, progress.workoutCount, progress.workoutCount)
    }
    val visible = "$weekOf · $count" + if (progress.isGoalMet) " ✓" else ""
    val spoken = "$weekOf, $count" + if (progress.isGoalMet) ", " + stringResource(R.string.training_history_week_goal_met) else ""
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics {
                heading()
                contentDescription = spoken
            }
    ) {
        Text(
            text = visible,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** A day header followed by that day's entries. */
private fun LazyListScope.dayItems(
    day: TrainingDay,
    names: WorkoutNameCatalog,
    processingPhotoEntryIds: Set<Long>,
    actions: HistoryRowActions
) {
    item(key = "day-${day.dateLocal}") {
        Text(
            text = dayLabel(day.label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { heading() }
        )
    }
    items(day.entries, key = { it.id }) { entry ->
        HistoryEntryRow(
            entry = entry,
            names = names,
            isProcessingPhoto = entry.id in processingPhotoEntryIds,
            actions = actions
        )
    }
}

/**
 * One entry. Logged activities open on tap (edit) and show their activity, duration,
 * intensity and the first line of the note; every entry can be deleted with a long press
 * or the accessibility action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryEntryRow(
    entry: TrainingLogEntry,
    names: WorkoutNameCatalog,
    isProcessingPhoto: Boolean,
    actions: HistoryRowActions
) {
    val deleteLabel = stringResource(R.string.training_history_delete_action)
    val editLabel = stringResource(R.string.log_activity_title_edit)
    val optionsLabel = stringResource(R.string.training_history_entry_actions)
    val photoLabel = stringResource(if (entry.photoPath == null) R.string.photo_add else R.string.photo_change)
    val removePhotoLabel = stringResource(R.string.photo_remove)
    var menuOpen by remember { mutableStateOf(false) }
    // Photos of logged activities are changed in their edit form; automatic entries here
    val photoActions = !entry.isActivity && !isProcessingPhoto
    val onDelete = { actions.onDelete(entry.id) }
    val interaction = if (entry.isActivity) {
        Modifier.combinedClickable(
            onClickLabel = editLabel,
            onLongClickLabel = deleteLabel,
            onLongClick = onDelete,
            onClick = { actions.onClick(entry.id) }
        )
    } else {
        Modifier.pointerInput(entry.id) { detectTapGestures(onLongPress = { menuOpen = true }) }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(interaction)
            .semantics(mergeDescendants = true) {
                if (!entry.isActivity) onLongClick(label = optionsLabel) { menuOpen = true; true }
                customActions = buildList {
                    if (photoActions) {
                        add(CustomAccessibilityAction(photoLabel) { actions.onAddPhoto(entry.id); true })
                        if (entry.photoPath != null) {
                            add(CustomAccessibilityAction(removePhotoLabel) { actions.onRemovePhoto(entry.id); true })
                        }
                    }
                    add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EntryIcon(entry)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WorkoutNameResolver.nameOf(entry, names).text(),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = entrySubtitle(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                noteFirstLine(entry)?.let { firstLine ->
                    Text(
                        text = firstLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!countsTowardGoal(entry)) {
                    Text(
                        text = stringResource(R.string.training_history_not_counted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isProcessingPhoto) {
                    Text(
                        text = stringResource(R.string.photo_processing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            entry.photoPath?.let { path ->
                TrainingPhotoThumbnail(
                    file = rememberThumbnailFile(path),
                    contentDescription = photoContentDescription(entry.completedAt),
                    onClick = { actions.onViewPhoto(entry.id) },
                    size = 48.dp
                )
            }
            Box {
                Text(
                    text = timeOf(entry.completedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (photoActions) {
                        DropdownMenuItem(
                            text = { Text(photoLabel) },
                            onClick = { menuOpen = false; actions.onAddPhoto(entry.id) }
                        )
                        if (entry.photoPath != null) {
                            DropdownMenuItem(
                                text = { Text(removePhotoLabel) },
                                onClick = { menuOpen = false; actions.onRemovePhoto(entry.id) }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(deleteLabel) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

/** The source icon, or the activity's own icon for a logged activity; decorative. */
@Composable
private fun EntryIcon(entry: TrainingLogEntry) {
    val activity = entry.activity
    if (entry.isActivity && activity != null) {
        Text(
            text = activityTypeIcon(activity),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics { }
        )
    } else {
        Icon(
            imageVector = sourceIcon(entry.source),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * "Gym routine · 32 min" (only the source when the duration is unknown); for a logged
 * activity "45 min · Moderate".
 */
@Composable
private fun entrySubtitle(entry: TrainingLogEntry): String {
    val duration = entry.durationSeconds?.let { seconds ->
        val minutes = durationMinutes(seconds)
        pluralStringResource(R.plurals.training_history_duration_minutes, minutes, minutes)
    }
    if (entry.isActivity) {
        val intensity = entry.intensityLevel?.let { stringResource(intensityLabel(it)) }
        return listOfNotNull(duration, intensity).joinToString(" · ")
    }
    val source = stringResource(sourceLabelRes(entry.source))
    return if (duration == null) source else stringResource(R.string.training_history_entry_subtitle, source, duration)
}

/** First non-blank line of a logged activity's note; null otherwise. */
internal fun noteFirstLine(entry: TrainingLogEntry): String? =
    entry.note?.takeIf { entry.isActivity }?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

/**
 * Whole minutes, rounded down, at least 1 (a short workout still shows "1 min"). Rounding
 * down keeps a 9:50 workout, which doesn't count toward the goal, from showing "10 min".
 */
internal fun durationMinutes(seconds: Int): Int = maxOf(1, seconds / 60)

@Composable
private fun dayLabel(label: DayLabel): String = when (label) {
    DayLabel.Today -> stringResource(R.string.training_history_today)
    DayLabel.Yesterday -> stringResource(R.string.training_history_yesterday)
    is DayLabel.Date -> formatDayHeader(label.dateLocal, LocalConfiguration.current.locales[0])
}

/** "Monday, 19 October" / "lunes, 19 de octubre", in the device locale's own order. */
private fun formatDayHeader(dateLocal: String, locale: Locale): String = formatDate(dateLocal, locale, "EEEEdMMMM")

/** A local date with the locale's best pattern for [skeleton] ("dMMMM" → "13 October" / "13 de octubre"). */
private fun formatDate(dateLocal: String, locale: Locale, skeleton: String): String {
    val date = SimpleDateFormat(LocalDateProvider.DATE_PATTERN, Locale.ROOT).parse(dateLocal) ?: return dateLocal
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    // Parsed and formatted in the same (default) zone, so the calendar date is kept
    return SimpleDateFormat(pattern, locale).format(date)
}

/** The entry's time in the device's 12/24-hour format. */
@Composable
private fun timeOf(epochMillis: Long): String =
    android.text.format.DateFormat.getTimeFormat(LocalContext.current).format(Date(epochMillis))

private fun sourceIcon(source: TrainingSource): ImageVector = when (source) {
    TrainingSource.DAILY -> Icons.Filled.DateRange
    TrainingSource.GYM -> Icons.Filled.Build
    TrainingSource.PROGRAM -> Icons.AutoMirrored.Filled.List
    // Fallback for an activity with an unknown type code; known types show their own icon
    TrainingSource.ACTIVITY -> Icons.Filled.Create
}

