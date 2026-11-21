package com.javargasp.tiominutermvi.ui.example

import com.javargasp.tiominutermvi.ui.base.MviIntent

sealed class ExampleIntent : MviIntent {
    data object OnLoad : ExampleIntent()
    data class OnButtonClick(val value: String) : ExampleIntent()
}