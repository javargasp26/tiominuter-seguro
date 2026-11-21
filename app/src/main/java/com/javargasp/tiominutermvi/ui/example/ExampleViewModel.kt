package com.javargasp.tiominutermvi.ui.example

import com.javargasp.tiominutermvi.ui.base.BaseViewModel

class ExampleViewModel : BaseViewModel<ExampleIntent, ExampleState, ExampleEffect>(
    initialState = ExampleState()
) {

    override suspend fun reduce(intent: ExampleIntent, currentState: ExampleState) {
        when (intent) {
            is ExampleIntent.OnLoad -> {
                setState(currentState.copy(isLoading = true))
                // Simulate loading
                setState(currentState.copy(isLoading = false, data = "Loaded data"))
            }
            
            is ExampleIntent.OnButtonClick -> {
                val newClicks = currentState.buttonClicks + 1
                setState(currentState.copy(buttonClicks = newClicks))
                
                if (newClicks >= 3) {
                    sendEffect(ExampleEffect.ShowMessage("Button clicked 3 times!"))
                }
                
                if (intent.value == "navigate") {
                    sendEffect(ExampleEffect.NavigateToNext)
                }
            }
        }
    }
}