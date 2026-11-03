package dev.example.bouncer.backend.routes

import dev.example.bouncer.backend.security.JwtTokenGateway
import dev.example.bouncer.backend.security.PolicyEnforcementPoint
import dev.example.bouncer.backend.security.principal
import dev.example.bouncer.shared.domain.ResourceNotFoundException
import dev.example.bouncer.shared.domain.auth.TokenPair
import dev.example.bouncer.shared.domain.model.ClientType
import dev.example.bouncer.shared.domain.policy.Action
import dev.example.bouncer.shared.domain.repository.UserRepository
import dev.example.bouncer.shared.domain.usecase.AuthenticateUserUseCase
import dev.example.bouncer.shared.domain.usecase.LogoutUseCase
import dev.example.bouncer.shared.domain.usecase.RefreshSessionUseCase
import dev.example.bouncer.shared.dto.LoginRequest
import dev.example.bouncer.shared.dto.LogoutRequest
import dev.example.bouncer.shared.dto.RefreshRequest
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import java.time.Instant

fun Route.authRoutes(
    authenticateUserUseCase: AuthenticateUserUseCase,
    refreshSessionUseCase: RefreshSessionUseCase,
    logoutUseCase: LogoutUseCase,
    tokenGateway: JwtTokenGateway,
    userRepository: UserRepository,
) {
    post("/auth/login", {
        description = "Authenticate with email/password. Rejects the login if the user's role " +
            "isn't allowed to authenticate from the declared client_type."
    }) {
        val request = call.receive<LoginRequest>()
        val clientType = parseClientType(request.clientType)
            ?: throw IllegalArgumentException(
                "invalid client_type '${request.clientType}', expected 'android' or 'web'",
            )
        val tokens = authenticateUserUseCase(request.email, request.password, clientType)
        call.application.log.info("login: {} from client_type={}\n{}", request.email, clientType, tokens.describe())
        call.respond(tokens.toResponse())
    }

    post("/auth/refresh", {
        description = "Validates and rotates a refresh token, issuing a new access token."
    }) {
        val request = call.receive<RefreshRequest>()
        val tokens = refreshSessionUseCase(request.refreshToken)
        call.application.log.info(
            "refresh: token {} rotated\n{}", request.refreshToken.fingerprint(), tokens.describe(),
        )
        call.respond(tokens.toResponse())
    }

    post("/auth/logout", {
        description = "Revokes the given refresh token."
    }) {
        val request = call.receive<LogoutRequest>()
        logoutUseCase(request.refreshToken)
        call.application.log.info("logout: refresh token {} revoked", request.refreshToken.fingerprint())
        call.respond(HttpStatusCode.NoContent)
    }

    get("/.well-known/jwks.json", {
        description = "Public RSA key set used to verify access tokens, available since startup."
    }) {
        call.respondText(tokenGateway.jwkSetJson(), ContentType.Application.Json)
    }

    get("/me", {
        description = "The authenticated user's own data."
    }) {
        val user = userRepository.findById(call.principal.userId) ?: throw ResourceNotFoundException()
        call.respond(user.toSummaryDto())
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.VIEW_SELF
    }
}

private fun parseClientType(raw: String): ClientType? =
    ClientType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

/** Tokens are never logged server-side: just enough of the tail to correlate a rotation, plus the expiry times. */
private fun String.fingerprint() = "…${takeLast(6)}"

private fun TokenPair.describe() =
    "    access token  expires ${Instant.ofEpochMilli(accessToken.expiresAtEpochMillis)}\n" +
        "    refresh token ${refreshToken.value.fingerprint()} expires " +
        Instant.ofEpochMilli(refreshToken.expiresAtEpochMillis)
