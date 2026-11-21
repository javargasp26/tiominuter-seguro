package com.javargasp.tiominutermvi.ui.example

import com.javargasp.tiominutermvi.ui.base.MviState

data class ExampleState(
    val isLoading: Boolean = false,
    val data: String = "",
    val buttonClicks: Int = 0
) : MviState