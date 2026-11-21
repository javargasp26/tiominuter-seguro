package com.javargasp.tiominutermvi.ui.logactivity

import com.javargasp.tiominutermvi.domain.traininglog.WorkoutSharing
import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryError
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryInput
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryRules
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.InvalidActivityEntryException
import com.javargasp.tiominutermvi.domain.traininglog.LocalDateProvider
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogEntry
import com.javargasp.tiominutermvi.domain.traininglog.TrainingLogRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.PhotoResult
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoError
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoRepository
import com.javargasp.tiominutermvi.domain.traininglog.photo.TrainingPhotoSource
import com.javargasp.tiominutermvi.domain.traininglog.photo.photoAction
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.traininglog.DayLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * ViewModel for the log-activity screen. With [entryId] null it creates an ACTIVITY entry;
 * otherwise it edits that entry (only ACTIVITY entries; others show not-found). Validation
 * uses [ActivityEntryRules]; the day can be today or one of the 6 days before (plus, when
 * editing, the entry's own day).
 *
 * Photo: a picked photo is processed right away ([TrainingPhotoRepository.stage], so the
 * form previews it and a bad image is reported at once) but only attached on Save; leaving
 * without saving discards it. Removing the stored photo also happens on Save. A photo that
 * can't be saved never loses the entry: it is saved and the error is shown.
 */
class LogActivityViewModel(
    private val entryId: Long?,
    private val repository: TrainingLogRepository,
    private val localDateProvider: LocalDateProvider,
    private val photos: TrainingPhotoRepository,
    private val sharing: WorkoutSharing = WorkoutSharing.None
) : BaseViewModel<LogActivityIntent, LogActivityState, LogActivityEffect>(
    initialState = LogActivityState(entryId = entryId)
) {

    /** The entry's own day when editing; it stays selectable even if older than the window. */
    private var originalDate: String? = null

    /** The entry saved, waiting for the share explanation to be answered before going back. */
    private var savedEntryId: Long? = null

    init {
        if (entryId == null) {
            val form = LogActivityForm(date = localDateProvider.today(), hour = 0, minute = 0).withDefaultTime()
            setState(
                state.value.copy(isLoading = false, form = form, initialForm = form, dayOptions = dayOptions())
            )
            viewModelScope.launch { setState(state.value.copy(canShare = sharing.isAvailable())) }
        } else {
            viewModelScope.launch { load(entryId) }
        }
    }

    private suspend fun load(id: Long) {
        val entry = try {
            repository.getEntry(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val type = entry?.activity
        if (entry == null || !entry.isActivity || type == null) {
            setState(state.value.copy(isLoading = false, isNotFound = true))
            return
        }
        originalDate = entry.dateLocal
        val form = entry.toForm(type)
        setState(
            state.value.copy(
                isLoading = false,
                form = form,
                initialForm = form,
                dayOptions = dayOptions(),
                timeEdited = true
            )
        )
        sharing.options(id)?.let { options ->
            setState(state.value.copy(canShare = true, shareInGroups = options.share))
        }
    }

    override suspend fun reduce(intent: LogActivityIntent, currentState: LogActivityState) {
        when (intent) {
            is LogActivityIntent.OnTypeSelected -> updateForm { it.copy(type = intent.type) }
            is LogActivityIntent.OnCustomNameChanged ->
                updateForm { it.copy(customName = intent.name.take(ActivityEntryRules.NAME_MAX_LENGTH)) }
            is LogActivityIntent.OnDurationChanged ->
                updateForm { it.copy(durationText = intent.text.filter(Char::isDigit).take(MAX_DURATION_DIGITS)) }
            is LogActivityIntent.OnQuickDurationSelected ->
                updateForm { it.copy(durationText = intent.minutes.toString()) }
            is LogActivityIntent.OnIntensitySelected -> updateForm { it.copy(intensity = intent.intensity) }
            is LogActivityIntent.OnDateSelected -> selectDate(intent.date)
            is LogActivityIntent.OnTimeSelected -> {
                setState(state.value.copy(timeEdited = true))
                updateForm { it.copy(hour = intent.hour, minute = intent.minute) }
            }
            is LogActivityIntent.OnNoteChanged ->
                updateForm { it.copy(note = intent.note.take(ActivityEntryRules.NOTE_MAX_LENGTH)) }
            is LogActivityIntent.OnPhotoSelected -> stagePhoto(intent.source)
            is LogActivityIntent.OnRemovePhoto -> {
                discardStaged(state.value.form?.photo)
                updateForm { it.copy(photo = FormPhoto.None) }
            }
            is LogActivityIntent.OnSave -> save()
            is LogActivityIntent.OnDeleteClicked -> if (state.value.isEditMode && state.value.form != null) {
                setState(state.value.copy(showDeleteConfirmation = true))
            }
            is LogActivityIntent.OnConfirmDelete -> delete()
            is LogActivityIntent.OnDismissDelete -> setState(state.value.copy(showDeleteConfirmation = false))
            is LogActivityIntent.OnBackPressed -> if (state.value.hasUnsavedChanges) {
                setState(state.value.copy(showDiscardConfirmation = true))
            } else {
                sendEffect(LogActivityEffect.NavigateBack)
            }
            is LogActivityIntent.OnConfirmDiscard -> {
                setState(state.value.copy(showDiscardConfirmation = false))
                sendEffect(LogActivityEffect.NavigateBack)
            }
            is LogActivityIntent.OnDismissDiscard -> setState(state.value.copy(showDiscardConfirmation = false))
            is LogActivityIntent.OnShareChanged -> setState(state.value.copy(shareInGroups = intent.share))
            is LogActivityIntent.OnShareIntroOk -> answerShareIntro(share = true)
            is LogActivityIntent.OnShareIntroDontShare -> answerShareIntro(share = false)
            is LogActivityIntent.OnShareIntroDismissed -> {
                // Not acknowledged: nothing is posted until the explanation is answered
                setState(state.value.copy(showShareIntro = false))
                sendEffect(LogActivityEffect.NavigateBack)
            }
        }
    }

    /** Applies [change] to the form; once errors are shown they follow every edit. */
    private fun updateForm(change: (LogActivityForm) -> LogActivityForm) {
        val current = state.value
        val form = current.form?.let(change) ?: return
        setState(
            current.copy(
                form = form,
                saveFailed = false,
                errors = if (current.showErrors) validate(form) else emptySet()
            )
        )
    }

    /** A new day keeps a time the person chose; otherwise it gets that day's default time. */
    private fun selectDate(date: String) {
        if (state.value.dayOptions.none { it.date == date }) return
        val timeEdited = state.value.timeEdited
        updateForm { form ->
            val withDate = form.copy(date = date)
            if (timeEdited) withDate else withDate.withDefaultTime()
        }
    }

    private suspend fun save() {
        val current = state.value
        val form = current.form ?: return
        // A photo still being processed would arrive after the save
        if (current.isSaving || current.isProcessingPhoto) return
        val errors = validate(form)
        if (errors.isNotEmpty()) {
            setState(current.copy(showErrors = true, errors = errors))
            return
        }
        setState(current.copy(isSaving = true, saveFailed = false))
        try {
            val input = form.toInput()
            val id = if (entryId == null) {
                repository.addActivityEntry(input)
            } else {
                repository.updateActivityEntry(entryId, input)
                entryId
            }
            // The entry is saved: a photo problem from here on is only reported
            val photoError = savePhoto(id, form.photo, current.initialForm?.photo)
            val showIntro = applyShareChoice(id)
            setState(state.value.copy(isSaving = false, initialForm = form, showShareIntro = showIntro))
            photoError?.let { sendEffect(LogActivityEffect.ShowPhotoError(it)) }
            if (showIntro) savedEntryId = id else sendEffect(LogActivityEffect.NavigateBack)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidActivityEntryException) {
            // E.g. midnight passed and the oldest day left the window
            setState(state.value.copy(isSaving = false, showErrors = true, errors = e.errors))
        } catch (e: Exception) {
            println("LogActivity: could not save: ${e.message}")
            setState(state.value.copy(isSaving = false, saveFailed = true))
        }
    }

    /**
     * The switch's choice for the saved entry [id]; true when the one-time explanation must show
     * first (a new shared workout). Only counted workouts are shared (options are null otherwise).
     */
    private suspend fun applyShareChoice(id: Long): Boolean {
        val current = state.value
        if (!current.canShare) return false
        val options = try {
            sharing.options(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false
        if (options.share != current.shareInGroups) sharing.setShare(id, current.shareInGroups)
        return current.shareInGroups && options.needsIntro
    }

    private suspend fun answerShareIntro(share: Boolean) {
        val id = savedEntryId ?: return
        if (!share) sharing.setShare(id, false)
        sharing.acknowledgeIntro()
        setState(state.value.copy(showShareIntro = false, shareInGroups = share))
        sendEffect(LogActivityEffect.NavigateBack)
    }

    /** Processes [source] into a staged photo for the form; the previous staged one is dropped. */
    private suspend fun stagePhoto(source: TrainingPhotoSource) {
        if (state.value.isProcessingPhoto || state.value.form == null) return
        setState(state.value.copy(isProcessingPhoto = true))
        when (val result = photoAction { photos.stage(source) }) {
            is PhotoResult.Success -> {
                discardStaged(state.value.form?.photo)
                setState(state.value.copy(isProcessingPhoto = false))
                updateForm { it.copy(photo = FormPhoto.Staged(result.value)) }
            }
            is PhotoResult.Failure -> {
                setState(state.value.copy(isProcessingPhoto = false))
                sendEffect(LogActivityEffect.ShowPhotoError(result.error))
            }
        }
    }

    /**
     * Applies the form's photo to the saved entry [id]: a staged photo is committed (it
     * replaces the old one), a removed photo is removed. Returns the error to show, if any.
     */
    private suspend fun savePhoto(id: Long, photo: FormPhoto, initial: FormPhoto?): TrainingPhotoError? {
        val result = when {
            photo == initial -> return null
            photo is FormPhoto.Staged -> photoAction { photos.commit(id, photo.staged) }
            photo is FormPhoto.None && initial is FormPhoto.Stored -> photoAction { photos.remove(id) }
            else -> return null
        }
        return (result as? PhotoResult.Failure)?.error
    }

    private fun discardStaged(photo: FormPhoto?) {
        if (photo is FormPhoto.Staged) photos.discard(photo.staged)
    }

    /** A staged photo that was never saved (Back, Discard, process end) is deleted. */
    override fun onCleared() {
        discardStaged(state.value.form?.photo)
    }

    private suspend fun delete() {
        val id = entryId ?: return
        setState(state.value.copy(showDeleteConfirmation = false))
        try {
            repository.deleteEntry(id)
            sendEffect(LogActivityEffect.NavigateBack)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("LogActivity: could not delete entry $id: ${e.message}")
            setState(state.value.copy(saveFailed = true))
        }
    }

    private fun validate(form: LogActivityForm): Set<ActivityEntryError> =
        ActivityEntryRules.validate(form.toInput(), localDateProvider.nowMillis(), earliestAllowed())

    private fun earliestAllowed(): Long {
        val windowStart = ActivityEntryRules.windowStart(localDateProvider)
        return originalDate?.let { minOf(windowStart, localDateProvider.startOfDay(it)) } ?: windowStart
    }

    /** Today and the 6 days before, plus the edited entry's own day when it is older. */
    private fun dayOptions(): List<DayOption> {
        val today = localDateProvider.today()
        val yesterday = localDateProvider.todayPlusDays(-1)
        val dates = (0..ActivityEntryRules.DAYS_BACK).map { localDateProvider.todayPlusDays(-it) }
        val all = dates + listOfNotNull(originalDate?.takeIf { it !in dates })
        return all.map { date ->
            DayOption(
                date = date,
                label = when (date) {
                    today -> DayLabel.Today
                    yesterday -> DayLabel.Yesterday
                    else -> DayLabel.Date(date)
                }
            )
        }
    }

    /** Now (to the minute) for today, [LogActivityState.PAST_DAY_DEFAULT_HOUR]:00 for a past day. */
    private fun LogActivityForm.withDefaultTime(): LogActivityForm =
        if (date == localDateProvider.today()) {
            val now = localDateProvider.timeOfDay(localDateProvider.nowMillis())
            copy(hour = now.hour, minute = now.minute)
        } else {
            copy(hour = LogActivityState.PAST_DAY_DEFAULT_HOUR, minute = 0)
        }

    private fun LogActivityForm.toInput() = ActivityEntryInput(
        type = type,
        customName = customName,
        durationMinutes = durationMinutes,
        intensity = intensity,
        completedAt = localDateProvider.epochMillisAt(date, hour, minute),
        note = note
    )

    private fun TrainingLogEntry.toForm(type: ActivityType): LogActivityForm {
        val time = localDateProvider.timeOfDay(completedAt)
        return LogActivityForm(
            type = type,
            customName = if (type == ActivityType.OTHER) title else "",
            durationText = durationSeconds?.let { (it / LogActivityState.SECONDS_PER_MINUTE).toString() }.orEmpty(),
            intensity = intensityLevel,
            date = dateLocal,
            hour = time.hour,
            minute = time.minute,
            note = note.orEmpty(),
            photo = photoPath?.let { FormPhoto.Stored(it) } ?: FormPhoto.None
        )
    }

    private companion object {
        const val MAX_DURATION_DIGITS = 3
    }
}
