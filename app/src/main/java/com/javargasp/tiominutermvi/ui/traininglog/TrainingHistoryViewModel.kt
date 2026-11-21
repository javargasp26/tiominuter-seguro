package com.javargasp.tiominutermvi.ui.traininglog

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoal
import com.javargasp.tiominutermvi.domain.goal.WeeklyGoalRepository
import com.javargasp.tiominutermvi.domain.goal.WeeklyProgressCalculator
import com.javargasp.tiominutermvi.domain.goal.countedWorkoutsPerDay
import com.javargasp.tiominutermvi.domain.traininglog.CalendarDates
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.WorkoutNameCatalogs
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoResult
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.photoAction
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for the Training history screen. Observes the history, the goal history and
 * the workout names ([WorkoutNameCatalogs]: the gym and daily catalogs, plus the program
 * workout of each PROGRAM entry) once (in [init]) and groups entries by week
 * (with that week's goal) and local day; Today / Yesterday come from [localDateProvider]
 * on every emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingHistoryViewModel(
    private val repository: TrainingLogRepository,
    private val weeklyGoalRepository: WeeklyGoalRepository,
    private val localDateProvider: LocalDateProvider,
    private val names: WorkoutNameCatalogs,
    private val photos: TrainingPhotoRepository
) : BaseViewModel<TrainingHistoryIntent, TrainingHistoryState, TrainingHistoryEffect>(
    initialState = TrainingHistoryState()
) {

    init {
        combine(
            repository.observeEntries(),
            weeklyGoalRepository.observeGoals(),
            names.observeCatalog()
        ) { entries, goals, catalog -> Triple(entries, goals, catalog) }
            // Program workouts are looked up per entry (one row each), after the catalogs
            .mapLatest { (entries, goals, catalog) ->
                val weeks = groupByWeek(
                    days = groupByDay(
                        entries = entries,
                        today = localDateProvider.today(),
                        yesterday = localDateProvider.todayPlusDays(-1)
                    ),
                    goals = goals
                )
                weeks to names.withProgramsOf(catalog, entries)
            }
            .onEach { (weeks, catalog) ->
                val current = state.value
                // A pending delete whose entry disappeared closes its dialog
                val pendingStillExists = weeks.any { week ->
                    week.days.any { day -> day.entries.any { it.id == current.pendingDeleteId } }
                }
                setState(
                    current.copy(
                        isLoading = false,
                        weeks = weeks,
                        pendingDeleteId = current.pendingDeleteId.takeIf { pendingStillExists },
                        names = catalog
                    )
                )
            }
            .launchIn(viewModelScope)
    }

    override suspend fun reduce(intent: TrainingHistoryIntent, currentState: TrainingHistoryState) {
        when (intent) {
            is TrainingHistoryIntent.OnDeleteRequested ->
                setState(state.value.copy(pendingDeleteId = intent.entryId))
            is TrainingHistoryIntent.OnConfirmDelete -> confirmDelete()
            is TrainingHistoryIntent.OnDismissDelete -> setState(state.value.copy(pendingDeleteId = null))
            is TrainingHistoryIntent.OnBackPressed -> sendEffect(TrainingHistoryEffect.NavigateBack)
            is TrainingHistoryIntent.OnEntryClicked -> {
                // Automatic entries (daily, gym, program) are not editable
                val entry = state.value.days.flatMap { it.entries }.firstOrNull { it.id == intent.entryId }
                if (entry?.isActivity == true) sendEffect(TrainingHistoryEffect.NavigateToEditActivity(entry.id))
            }
            is TrainingHistoryIntent.OnLogActivityClicked -> sendEffect(TrainingHistoryEffect.NavigateToLogActivity)
            is TrainingHistoryIntent.OnPhotoSelected -> attachPhoto(intent.entryId, intent.source)
            is TrainingHistoryIntent.OnRemovePhotoRequested -> {
                val entry = state.value.entry(intent.entryId)
                if (entry != null && canEditPhotoHere(entry) && entry.photoPath != null) {
                    setState(state.value.copy(pendingPhotoRemovalId = entry.id))
                }
            }
            is TrainingHistoryIntent.OnConfirmRemovePhoto -> removePhoto()
            is TrainingHistoryIntent.OnDismissRemovePhoto -> setState(state.value.copy(pendingPhotoRemovalId = null))
        }
    }

    /** Logged activities change their photo in their edit form; here only automatic entries. */
    private fun canEditPhotoHere(entry: TrainingLogEntry): Boolean = !entry.isActivity

    private suspend fun attachPhoto(entryId: Long, source: TrainingPhotoSource) {
        val entry = state.value.entry(entryId) ?: return
        if (!canEditPhotoHere(entry) || entryId in state.value.processingPhotoEntryIds) return
        setState(state.value.copy(processingPhotoEntryIds = state.value.processingPhotoEntryIds + entryId))
        val result = photoAction { photos.attach(entryId, source) }
        // The list updates from the database; only the progress flag is cleared here
        setState(state.value.copy(processingPhotoEntryIds = state.value.processingPhotoEntryIds - entryId))
        if (result is PhotoResult.Failure) sendEffect(TrainingHistoryEffect.ShowPhotoError(result.error))
    }

    private suspend fun removePhoto() {
        val entry = state.value.pendingPhotoRemovalEntry
        setState(state.value.copy(pendingPhotoRemovalId = null))
        if (entry == null || entry.id in state.value.processingPhotoEntryIds) return
        setState(state.value.copy(processingPhotoEntryIds = state.value.processingPhotoEntryIds + entry.id))
        val result = photoAction { photos.remove(entry.id) }
        setState(state.value.copy(processingPhotoEntryIds = state.value.processingPhotoEntryIds - entry.id))
        if (result is PhotoResult.Failure) sendEffect(TrainingHistoryEffect.ShowPhotoError(result.error))
    }

    private suspend fun confirmDelete() {
        val id = state.value.pendingDeleteId ?: return
        setState(state.value.copy(pendingDeleteId = null))
        try {
            repository.deleteEntry(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("TrainingHistory: could not delete entry $id: ${e.message}")
        }
    }

    companion object {
        /**
         * Groups day groups (newest first) by their week, with each week's goal progress
         * (counted workouts only, see countsTowardGoal).
         */
        internal fun groupByWeek(days: List<TrainingDay>, goals: List<WeeklyGoal>): List<TrainingWeek> {
            val workoutsPerDay = countedWorkoutsPerDay(days.flatMap { it.entries })
            return days.groupBy { CalendarDates.mondayOf(it.dateLocal) }
                .map { (weekStart, weekDays) ->
                    TrainingWeek(
                        weekStart = weekStart,
                        progress = WeeklyProgressCalculator.progress(weekStart, goals, workoutsPerDay),
                        days = weekDays
                    )
                }
        }

        /** Groups entries (already newest first) by dateLocal, keeping that order. */
        internal fun groupByDay(entries: List<TrainingLogEntry>, today: String, yesterday: String): List<TrainingDay> =
            entries.groupBy { it.dateLocal }
                .map { (date, dayEntries) ->
                    TrainingDay(
                        dateLocal = date,
                        label = when (date) {
                            today -> DayLabel.Today
                            yesterday -> DayLabel.Yesterday
                            else -> DayLabel.Date(date)
                        },
                        entries = dayEntries.sortedByDescending { it.completedAt }
                    )
                }
                .sortedByDescending { it.dateLocal }
    }
}
