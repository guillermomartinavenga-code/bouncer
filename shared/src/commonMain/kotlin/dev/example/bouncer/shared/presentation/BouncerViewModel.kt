package dev.example.bouncer.shared.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The lightweight ViewModel shared by Android and Web: a plain class built on `CoroutineScope` +
 * `StateFlow`, with no multiplatform `androidx.lifecycle` dependency. Each screen's concrete
 * ViewModel extends this, invokes domain use cases, and reports results through [uiState].
 */
abstract class BouncerViewModel<T> {

    private val viewModelJob = SupervisorJob()
    protected val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Default + viewModelJob)

    private val _uiState = MutableStateFlow<UiState<T>>(UiState.Loading)
    val uiState: StateFlow<UiState<T>> = _uiState.asStateFlow()

    protected fun setState(state: UiState<T>) {
        _uiState.value = state
    }

    protected fun launch(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(block = block)
    }

    /** Call when the screen holding this ViewModel is discarded, to cancel in-flight work. */
    open fun onCleared() {
        viewModelJob.cancel()
    }
}
