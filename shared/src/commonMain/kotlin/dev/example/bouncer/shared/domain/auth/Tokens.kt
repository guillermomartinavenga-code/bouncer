package dev.example.bouncer.shared.domain.auth

import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.model.User

data class AccessToken(val value: String, val expiresAtEpochMillis: Long)

data class RefreshToken(val value: String, val expiresAtEpochMillis: Long)

data class TokenPair(val accessToken: AccessToken, val refreshToken: RefreshToken)

data class AuthenticatedPrincipal(val userId: String, val role: Role)

interface PasswordHasher {
    fun hash(plainPassword: String): String
    fun verify(plainPassword: String, hash: String): Boolean
}

/**
 * Port for everything token-related. The domain only knows "valid token" vs. "how it was
 * obtained" — the concrete signing/verification mechanism (JWT + JWKS today) lives entirely in
 * the backend adapter that implements this interface, so a future OAuth/OIDC provider can replace
 * it without touching the use cases below.
 */
interface TokenGateway {
    suspend fun issueTokenPair(user: User): TokenPair
    suspend fun rotateRefreshToken(refreshToken: String): TokenPair
    suspend fun revokeRefreshToken(refreshToken: String)
    suspend fun verifyAccessToken(accessToken: String): AuthenticatedPrincipal
}
