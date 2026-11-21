package com.javargasp.tiominutermvi.ui.groups.list

import com.javargasp.tiominutermvi.domain.groups.GroupError
import com.javargasp.tiominutermvi.domain.groups.GroupException
import com.javargasp.tiominutermvi.domain.groups.GroupOperations
import com.javargasp.tiominutermvi.ui.base.BaseViewModel
import com.javargasp.tiominutermvi.ui.groups.common.toText

/**
 * My groups (the index reconciled on every load) with my position this week; guests see an
 * explanation and "Sign in with Google" (the Account screen's flow). Creating or joining needs a
 * weekly goal (the goal screen is reused) and room for one more group.
 */
class GroupListViewModel(
    private val operations: GroupOperations
) : BaseViewModel<GroupListIntent, GroupListState, GroupListEffect>(GroupListState()) {

    override suspend fun reduce(intent: GroupListIntent, currentState: GroupListState) {
        when (intent) {
            GroupListIntent.OnStart, GroupListIntent.OnRetry -> load()
            GroupListIntent.OnCreateClicked -> startAdding(GoalRequiredFor.CREATE)
            GroupListIntent.OnJoinClicked -> startAdding(GoalRequiredFor.JOIN)
            is GroupListIntent.OnGroupClicked -> sendEffect(GroupListEffect.NavigateToDetail(intent.groupId))
            GroupListIntent.OnSignInClicked -> sendEffect(GroupListEffect.NavigateToAccount)
            GroupListIntent.OnSetGoalClicked -> {
                setState(state.value.copy(goalRequiredFor = null))
                sendEffect(GroupListEffect.NavigateToWeeklyGoal)
            }
            GroupListIntent.OnGoalDialogDismissed -> setState(state.value.copy(goalRequiredFor = null))
            GroupListIntent.OnBackClicked -> sendEffect(GroupListEffect.NavigateBack)
        }
    }

    private suspend fun load() {
        try {
            operations.currentUser()
        } catch (e: GroupException) {
            setState(state.value.copy(isGuest = true, isLoading = false, groups = emptyList(), loadError = false))
            return
        }
        setState(state.value.copy(isGuest = false, isLoading = true, loadError = false))
        try {
            setState(state.value.copy(isLoading = false, groups = operations.myGroups()))
        } catch (e: GroupException) {
            if (e.error == GroupError.NOT_SIGNED_IN) {
                setState(state.value.copy(isGuest = true, isLoading = false))
            } else {
                setState(state.value.copy(isLoading = false, loadError = state.value.groups.isEmpty()))
                if (state.value.groups.isNotEmpty()) sendEffect(GroupListEffect.ShowMessage(e.error.toText()))
            }
        }
    }

    private suspend fun startAdding(target: GoalRequiredFor) {
        when {
            !state.value.canAddGroup -> sendEffect(GroupListEffect.ShowMessage(GroupError.GROUP_LIMIT.toText()))
            !operations.hasWeeklyGoal() -> setState(state.value.copy(goalRequiredFor = target))
            target == GoalRequiredFor.CREATE -> sendEffect(GroupListEffect.NavigateToCreate)
            else -> sendEffect(GroupListEffect.NavigateToJoin)
        }
    }
}
