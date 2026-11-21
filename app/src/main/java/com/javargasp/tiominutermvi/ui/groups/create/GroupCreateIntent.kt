package com.javargasp.tiominutermvi.ui.groups.create

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed interface GroupCreateIntent : MviIntent {
    data class OnNameChanged(val name: String) : GroupCreateIntent
    data object OnCreateClicked : GroupCreateIntent
    data object OnCopyClicked : GroupCreateIntent
    data object OnShareClicked : GroupCreateIntent
    data object OnOpenGroupClicked : GroupCreateIntent
    data object OnBackClicked : GroupCreateIntent
}
