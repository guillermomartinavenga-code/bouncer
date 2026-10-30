package dev.example.bouncer.shared.presentation

import dev.example.bouncer.shared.common.ErrorType

/**
 * Loading/Content/Error screen state, shared verbatim by Android and Web so both platforms model
 * a screen the same way. An empty list is `Content(emptyList())`, never a fourth state.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val type: ErrorType, val message: String) : UiState<Nothing>
}
