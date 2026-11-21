package com.javargasp.tiominutermvi.ui.gymroutineselection

import com.javargasp.tiominutermvi.domain.model.GymRoutine
import com.javargasp.tiominutermvi.ui.base.MviState

/**
 * State for the Gym Routine Selection screen.
 *
 * [isLoading] is true only while there is no data yet; once routines arrive it
 * stays false and [isRefreshing] takes over for subsequent refreshes.
 * [errorMessage] is only set when there is no data and a refresh failed.
 */
data class GymRoutineSelectionState(
    val isLoading: Boolean = true,
    val routines: List<GymRoutine> = emptyList(),
    val expandedRoutineIds: Set<Int> = emptySet(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
) : MviState
