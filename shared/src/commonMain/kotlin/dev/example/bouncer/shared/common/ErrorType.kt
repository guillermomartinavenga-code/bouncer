package dev.example.bouncer.shared.common

/**
 * Error classification shared by the backend's HTTP error envelope and the client-side
 * `UiState.Error` (introduced in Phase 3). Exactly these four — the backend never emits NETWORK
 * (that's a client-side-only condition: no response reached the app at all).
 */
enum class ErrorType { UNAUTHORIZED, FORBIDDEN, NETWORK, UNKNOWN }
