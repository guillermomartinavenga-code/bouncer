package dev.example.bouncer.shared.presentation

import dev.example.bouncer.shared.client.BouncerApiClient
import dev.example.bouncer.shared.common.ErrorType
import dev.example.bouncer.shared.dto.UserResponse

/**
 * Drives the app root. `Content(null)` = signed out (show the login form), `Content(user)` =
 * signed in, `Loading` = a login/restore is in flight, `Error` = it failed (login form + message).
 */
class SessionViewModel(
    private val api: BouncerApiClient,
    private val clientType: String,
) : BouncerViewModel<UserResponse?>() {

    init {
        setState(UiState.Content(null))
    }

    fun login(email: String, password: String) = launch {
        setState(UiState.Loading)
        when (val result = api.login(email, password, clientType)) {
            is UiState.Content -> loadProfile()
            is UiState.Error -> setState(result)
            UiState.Loading -> Unit
        }
    }

    /** Resumes a saved session (after the Android biometric unlock) with a fresh token pair. */
    fun restore() = launch {
        setState(UiState.Loading)
        when (val result = api.restoreSession()) {
            is UiState.Content -> loadProfile()
            is UiState.Error -> setState(sessionExpired(result))
            UiState.Loading -> Unit
        }
    }

    fun logout() = launch {
        api.logout()
        setState(UiState.Content(null))
    }

    /** Called by a screen that received a persistent 401: the refresh token is gone too. */
    fun onSessionExpired() = launch {
        api.clearLocalSession()
        setState(UiState.Error(ErrorType.UNAUTHORIZED, SESSION_EXPIRED_MESSAGE))
    }

    private suspend fun loadProfile() {
        when (val profile = api.me()) {
            is UiState.Content -> setState(UiState.Content(profile.data))
            is UiState.Error -> setState(profile)
            UiState.Loading -> Unit
        }
    }

    private fun sessionExpired(error: UiState.Error): UiState.Error =
        if (error.type == ErrorType.UNAUTHORIZED) error.copy(message = SESSION_EXPIRED_MESSAGE) else error

    private companion object {
        const val SESSION_EXPIRED_MESSAGE = "Your session expired, please log in again."
    }
}
