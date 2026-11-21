package com.javargasp.tiominutermvi.ui.base

import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Helper composable to collect effects in a lifecycle-aware manner
 */
@Composable
fun <E : MviEffect> CollectEffects(
    effectFlow: Flow<E>,
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: (E) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    LaunchedEffect(effectFlow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(lifecycleState) {
            effectFlow.collect { effect ->
                onEffect(effect)
            }
        }
    }
}

/**
 * MVI pattern composable that connects a ViewModel to the UI
 */
@Composable
fun <I : MviIntent, S : MviState, E : MviEffect> MviComposable(
    viewModel: BaseViewModel<I, S, E>,
    onEffect: (E) -> Unit,
    content: @Composable (state: S, onIntent: (I) -> Unit) -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Collect effects
    CollectEffects(viewModel.effect, onEffect = onEffect)
    
    content(state, viewModel::handleIntent)
}