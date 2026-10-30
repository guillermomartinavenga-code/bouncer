package dev.example.bouncer.backend.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.example.bouncer.shared.domain.InvalidOrExpiredTokenException
import dev.example.bouncer.shared.domain.auth.AccessToken
import dev.example.bouncer.shared.domain.auth.AuthenticatedPrincipal
import dev.example.bouncer.shared.domain.auth.RefreshToken
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.auth.TokenPair
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.model.User
import java.text.ParseException
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Issues and verifies RS256 JWT access tokens and opaque, rotating refresh tokens entirely
 * in-memory. The resource server (verifyAccessToken) only ever checks a signature against the
 * public key below — never anything about how the token was produced — so a future OAuth/OIDC
 * authorization server could replace token issuance without this verification path changing.
 */
class JwtTokenGateway(private val clock: () -> Long = System::currentTimeMillis) : TokenGateway {

    private val rsaKey: RSAKey = RSAKeyGenerator(2048)
        .keyID(UUID.randomUUID().toString())
        .generate()

    private val signer = RSASSASigner(rsaKey)
    private val verifier = RSASSAVerifier(rsaKey.toRSAPublicKey())

    private data class RefreshRecord(val userId: String, val role: Role, val expiresAt: Long)

    private val refreshTokens = ConcurrentHashMap<String, RefreshRecord>()

    companion object {
        const val ACCESS_TOKEN_TTL_MILLIS = 5 * 60 * 1000L
        const val REFRESH_TOKEN_TTL_MILLIS = 10 * 60 * 1000L
        const val ISSUER = "bouncer"
        const val ROLE_CLAIM = "role"
    }

    /** JSON for GET /.well-known/jwks.json — the public key only, never the private key material. */
    fun jwkSetJson(): String = JWKSet(rsaKey.toPublicJWK()).toString()

    override suspend fun issueTokenPair(user: User): TokenPair =
        TokenPair(signAccessToken(user.id, user.role), createRefreshToken(user.id, user.role))

    override suspend fun rotateRefreshToken(refreshToken: String): TokenPair {
        val record = refreshTokens.remove(refreshToken)
            ?: throw InvalidOrExpiredTokenException("refresh token not recognized or already used")
        if (record.expiresAt < clock()) {
            throw InvalidOrExpiredTokenException("refresh token expired")
        }
        return TokenPair(
            signAccessToken(record.userId, record.role),
            createRefreshToken(record.userId, record.role),
        )
    }

    override suspend fun revokeRefreshToken(refreshToken: String) {
        refreshTokens.remove(refreshToken)
    }

    override suspend fun verifyAccessToken(accessToken: String): AuthenticatedPrincipal {
        val signedJwt = try {
            SignedJWT.parse(accessToken)
        } catch (e: ParseException) {
            throw InvalidOrExpiredTokenException("malformed access token")
        }

        if (!signedJwt.verify(verifier)) {
            throw InvalidOrExpiredTokenException("invalid access token signature")
        }

        val claims = signedJwt.jwtClaimsSet
        val expiresAt = claims.expirationTime ?: throw InvalidOrExpiredTokenException("access token has no expiry")
        if (expiresAt.before(Date(clock()))) {
            throw InvalidOrExpiredTokenException("access token expired")
        }
        if (claims.issuer != ISSUER) {
            throw InvalidOrExpiredTokenException("unknown token issuer")
        }

        val role = runCatching { Role.valueOf(claims.getStringClaim(ROLE_CLAIM)) }
            .getOrElse { throw InvalidOrExpiredTokenException("access token missing a valid role claim") }

        return AuthenticatedPrincipal(userId = claims.subject, role = role)
    }

    private fun signAccessToken(userId: String, role: Role): AccessToken {
        val now = clock()
        val expiresAt = now + ACCESS_TOKEN_TTL_MILLIS
        val claims = JWTClaimsSet.Builder()
            .subject(userId)
            .claim(ROLE_CLAIM, role.name)
            .issuer(ISSUER)
            .issueTime(Date(now))
            .expirationTime(Date(expiresAt))
            .build()
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.keyID)
            .type(JOSEObjectType.JWT)
            .build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(signer)
        return AccessToken(signedJwt.serialize(), expiresAt)
    }

    private fun createRefreshToken(userId: String, role: Role): RefreshToken {
        val value = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val expiresAt = clock() + REFRESH_TOKEN_TTL_MILLIS
        refreshTokens[value] = RefreshRecord(userId, role, expiresAt)
        return RefreshToken(value, expiresAt)
    }
}
