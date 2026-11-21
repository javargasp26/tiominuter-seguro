package com.javargasp.tiominutermvi.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel implementing the MVI pattern.
 * 
 * @param I The type of Intent this ViewModel handles
 * @param S The type of State this ViewModel manages
 * @param E The type of Effect this ViewModel emits
 */
abstract class BaseViewModel<I : MviIntent, S : MviState, E : MviEffect>(
    initialState: S
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = Channel<E>()
    val effect: Flow<E> = _effect.receiveAsFlow()

    /**
     * Handle incoming intents from the UI
     */
    fun handleIntent(intent: I) {
        viewModelScope.launch {
            reduce(intent, _state.value)
        }
    }

    /**
     * Process the intent and update state or emit effects
     */
    protected abstract suspend fun reduce(intent: I, currentState: S)

    /**
     * Update the current state
     */
    protected fun setState(newState: S) {
        _state.value = newState
    }

    /**
     * Emit a one-time effect
     */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }
}