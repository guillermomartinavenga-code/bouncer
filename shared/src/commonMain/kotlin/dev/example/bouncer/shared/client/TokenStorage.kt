package dev.example.bouncer.shared.client

/**
 * Where the client persists its current token pair between requests (and, on Android, between
 * app launches, behind the Keystore + biometric unlock added in Phase 4). The HTTP client only
 * depends on this interface, never on a concrete storage mechanism.
 */
interface TokenStorage {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun saveTokens(accessToken: String, refreshToken: String)
    suspend fun clear()
}

/** Plain in-memory storage: used for tests, and as the default before Phase 4 adds a Keystore-backed one. */
class InMemoryTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override suspend fun getAccessToken(): String? = accessToken

    override suspend fun getRefreshToken(): String? = refreshToken

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override suspend fun clear() {
        accessToken = null
        refreshToken = null
    }
}
