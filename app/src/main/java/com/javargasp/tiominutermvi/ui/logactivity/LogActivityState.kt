package com.javargasp.tiominutermvi.ui.logactivity

import com.javargasp.tiominutermvi.domain.goal.MIN_COUNTED_DURATION_SECONDS
import com.javargasp.tiominutermvi.domain.traininglog.ActivityEntryError
import com.javargasp.tiominutermvi.domain.traininglog.ActivityType
import com.javargasp.tiominutermvi.domain.traininglog.Intensity
import com.javargasp.tiominutermvi.domain.traininglog.photo.StagedPhoto
import com.javargasp.tiominutermvi.ui.base.MviState
import com.javargasp.tiominutermvi.ui.traininglog.DayLabel

/**
 * The photo in the form: none, the entry's stored photo (edit mode) or a new photo already
 * processed but only attached on Save.
 */
sealed interface FormPhoto {
    data object None : FormPhoto
    data class Stored(val photoPath: String) : FormPhoto
    data class Staged(val staged: StagedPhoto) : FormPhoto
}

/** What the person has entered. [durationText] holds digits only; [date] is "yyyy-MM-dd". */
data class LogActivityForm(
    val type: ActivityType? = null,
    val customName: String = "",
    val durationText: String = "",
    val intensity: Intensity? = null,
    val date: String,
    val hour: Int,
    val minute: Int,
    val note: String = "",
    val photo: FormPhoto = FormPhoto.None
) {
    val durationMinutes: Int? get() = durationText.toIntOrNull()
}

/** One selectable day: Today, Yesterday or a date (formatted by the screen). */
data class DayOption(val date: String, val label: DayLabel)

/**
 * State for the log-activity screen. [entryId] is null when creating. [form] is null
 * while an entry loads; [initialForm] is what was loaded (or the defaults), to detect
 * unsaved changes. [errors] are shown after the first Save attempt and then follow edits.
 */
data class LogActivityState(
    val isLoading: Boolean = true,
    val entryId: Long? = null,
    val form: LogActivityForm? = null,
    val initialForm: LogActivityForm? = null,
    val dayOptions: List<DayOption> = emptyList(),
    val showErrors: Boolean = false,
    val errors: Set<ActivityEntryError> = emptySet(),
    val timeEdited: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val isNotFound: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    /** A picked photo is being processed (the photo buttons are disabled meanwhile). */
    val isProcessingPhoto: Boolean = false,
    /** "Share in my groups" shows (signed in, in a group; when editing, a workout still shared or not). */
    val canShare: Boolean = false,
    /** The switch, ON by default; for this workout only (not part of the form's unsaved changes). */
    val shareInGroups: Boolean = true,
    /** The one-time explanation, after the save of the first shared workout. */
    val showShareIntro: Boolean = false
) : MviState {

    val isEditMode: Boolean get() = entryId != null

    val hasUnsavedChanges: Boolean get() = form != null && form != initialForm

    /** A valid duration that won't count toward the weekly goal (under the minimum). */
    val showsShortDurationHint: Boolean
        get() {
            val minutes = form?.durationMinutes ?: return false
            return minutes in 1 until MIN_COUNTED_DURATION_SECONDS / SECONDS_PER_MINUTE
        }

    companion object {
        const val SECONDS_PER_MINUTE = 60
        val QUICK_DURATIONS = listOf(15, 30, 45, 60, 90)

        /** Default time for a past day; today defaults to now. */
        const val PAST_DAY_DEFAULT_HOUR = 18
    }
}
