package com.javargasp.tiominutermvi.ui.example

import com.javargasp.tiominutermvi.ui.base.MviEffect

sealed class ExampleEffect : MviEffect {
    data class ShowMessage(val message: String) : ExampleEffect()
    data object NavigateToNext : ExampleEffect()
}