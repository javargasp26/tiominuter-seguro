package com.javargasp.tiominutermvi.ui.program.create

import com.javargasp.tiominutermvi.domain.program.model.ProgramFocus
import com.javargasp.tiominutermvi.domain.program.model.ProgramGoal
import com.javargasp.tiominutermvi.domain.program.model.ProgramLevel
import com.javargasp.tiominutermvi.domain.program.model.ProgramLocation
import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface ProgramCreateIntent : MviIntent {
    data class OnAgeChanged(val text: String) : ProgramCreateIntent
    data class OnWeightChanged(val text: String) : ProgramCreateIntent
    data class OnHeightChanged(val text: String) : ProgramCreateIntent
    data class OnLevelSelected(val level: ProgramLevel) : ProgramCreateIntent
    data class OnGoalSelected(val goal: ProgramGoal) : ProgramCreateIntent
    data class OnFocusSelected(val focus: ProgramFocus) : ProgramCreateIntent
    data class OnLocationSelected(val location: ProgramLocation) : ProgramCreateIntent
    data class OnWeeksChanged(val weeks: Int) : ProgramCreateIntent
    data class OnDaysPerWeekChanged(val days: Int) : ProgramCreateIntent
    data object OnPreviewClicked : ProgramCreateIntent
    data object OnDismissPreview : ProgramCreateIntent
    data object OnCreateClicked : ProgramCreateIntent
    data object OnBackPressed : ProgramCreateIntent
}
