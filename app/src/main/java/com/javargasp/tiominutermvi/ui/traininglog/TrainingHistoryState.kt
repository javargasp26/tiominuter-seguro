package com.javargasp.tiominutermvi.ui.traininglog

import com.javargasp.tiominutermvi.domain.goal.WeeklyProgress
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutName
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalog
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameResolver
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * State for the Training history screen: entries grouped by week (with that week's goal
 * progress) and, inside, by local day, newest first. [pendingDeleteId] is the entry whose
 * delete confirmation is open. [names] lets every entry be named in the current language
 * ([nameOf], through [WorkoutNameResolver]); an entry whose routine or program workout is
 * unknown keeps its stored title.
 */
data class TrainingHistoryState(
    val isLoading: Boolean = true,
    val weeks: List<TrainingWeek> = emptyList(),
    val pendingDeleteId: Long? = null,
    val names: WorkoutNameCatalog = WorkoutNameCatalog.EMPTY,
    /** Entries whose photo is being saved (their photo actions are disabled meanwhile). */
    val processingPhotoEntryIds: Set<Long> = emptySet(),
    /** The entry whose "Remove the photo?" confirmation is open. */
    val pendingPhotoRemovalId: Long? = null
) : MviState {

    /** Every day group, newest first. */
    val days: List<TrainingDay> get() = weeks.flatMap { it.days }

    val isEmpty: Boolean get() = !isLoading && days.isEmpty()

    /** The entry being confirmed for deletion, while it still exists. */
    val pendingDeleteEntry: TrainingLogEntry?
        get() = pendingDeleteId?.let(::entry)

    /** The entry whose photo removal is being confirmed, while it still has a photo. */
    val pendingPhotoRemovalEntry: TrainingLogEntry?
        get() = pendingPhotoRemovalId?.let(::entry)?.takeIf { it.photoPath != null }

    /** What [entry] is called (rendered in the viewer's language by the screen). */
    fun nameOf(entry: TrainingLogEntry): WorkoutName = WorkoutNameResolver.nameOf(entry, names)

    fun entry(id: Long): TrainingLogEntry? = days.firstNotNullOfOrNull { day -> day.entries.firstOrNull { it.id == id } }
}

/** One week (Monday [weekStart]) with its goal progress and its day groups, newest first. */
data class TrainingWeek(
    val weekStart: String,
    val progress: WeeklyProgress,
    val days: List<TrainingDay>
)

/** The entries of one local date ("yyyy-MM-dd"), newest first, with its header label. */
data class TrainingDay(
    val dateLocal: String,
    val label: DayLabel,
    val entries: List<TrainingLogEntry>
)

/** Header of a day: Today, Yesterday, or the date itself (formatted by the screen). */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class Date(val dateLocal: String) : DayLabel
}
