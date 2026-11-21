package com.javargasp.tiominutermvi.ui.program.create

import androidx.lifecycle.viewModelScope
import com.javargasp.tiominutermvi.domain.program.model.ProgramProfile
import com.javargasp.tiominutermvi.domain.program.repository.ProgramRepository
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Create form: validation, BMI, week-1 preview and creation. */
class ProgramCreateViewModel(
    private val repository: ProgramRepository
) : BaseViewModel<ProgramCreateIntent, ProgramCreateState, ProgramCreateEffect>(ProgramCreateState()) {

    init {
        viewModelScope.launch {
            try {
                setState(state.value.copy(impactRules = repository.getImpactRules()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without the rules the low-impact note is simply not shown.
            }
        }
    }

    override suspend fun reduce(intent: ProgramCreateIntent, currentState: ProgramCreateState) {
        val s = state.value
        when (intent) {
            is ProgramCreateIntent.OnAgeChanged -> setState(s.copy(ageText = digits(intent.text, 2)))
            is ProgramCreateIntent.OnWeightChanged -> setState(s.copy(weightText = decimal(intent.text)))
            is ProgramCreateIntent.OnHeightChanged -> setState(s.copy(heightText = digits(intent.text, 3)))
            is ProgramCreateIntent.OnLevelSelected -> setState(s.copy(level = intent.level))
            is ProgramCreateIntent.OnGoalSelected -> setState(s.copy(goal = intent.goal))
            is ProgramCreateIntent.OnFocusSelected -> setState(s.copy(focus = intent.focus))
            is ProgramCreateIntent.OnLocationSelected -> setState(s.copy(location = intent.location))
            is ProgramCreateIntent.OnWeeksChanged ->
                setState(s.copy(weeks = intent.weeks.coerceIn(ProgramProfile.WEEKS_RANGE)))
            is ProgramCreateIntent.OnDaysPerWeekChanged ->
                setState(s.copy(daysPerWeek = intent.days.coerceIn(ProgramProfile.DAYS_PER_WEEK_RANGE)))
            ProgramCreateIntent.OnPreviewClicked -> preview()
            ProgramCreateIntent.OnDismissPreview -> setState(s.copy(preview = null))
            ProgramCreateIntent.OnCreateClicked -> create()
            ProgramCreateIntent.OnBackPressed -> sendEffect(ProgramCreateEffect.NavigateBack)
        }
    }

    private suspend fun preview() {
        val profile = state.value.profile ?: return setState(state.value.copy(showAllErrors = true))
        val week1 = repository.previewProgram(profile).weeks.firstOrNull()?.workouts.orEmpty()
        setState(state.value.copy(preview = week1, exercises = repository.getExercises()))
    }

    private suspend fun create() {
        if (state.value.isWorking) return
        val profile = state.value.profile ?: return setState(state.value.copy(showAllErrors = true))
        setState(state.value.copy(isWorking = true, createFailed = false, preview = null))
        try {
            val id = repository.createProgram(profile)
            sendEffect(ProgramCreateEffect.NavigateToDetail(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setState(state.value.copy(isWorking = false, createFailed = true))
        }
    }

    private fun digits(text: String, maxLength: Int): String = text.filter { it.isDigit() }.take(maxLength)

    /** Digits plus one decimal separator, at most 6 characters ("120.5"). */
    private fun decimal(text: String): String {
        var separatorSeen = false
        return text.filter { c ->
            when {
                c.isDigit() -> true
                (c == '.' || c == ',') && !separatorSeen -> { separatorSeen = true; true }
                else -> false
            }
        }.take(6)
    }
}
