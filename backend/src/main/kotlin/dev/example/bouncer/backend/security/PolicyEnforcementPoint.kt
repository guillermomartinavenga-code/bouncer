package dev.example.bouncer.backend.security

import dev.example.bouncer.shared.domain.ForbiddenException
import dev.example.bouncer.shared.domain.InvalidOrExpiredTokenException
import dev.example.bouncer.shared.domain.ResourceNotFoundException
import dev.example.bouncer.shared.domain.auth.AuthenticatedPrincipal
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.policy.Action
import dev.example.bouncer.shared.domain.policy.Decision
import dev.example.bouncer.shared.domain.policy.PolicyDecisionPoint
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.util.AttributeKey

private val PrincipalKey = AttributeKey<AuthenticatedPrincipal>("AuthenticatedPrincipal")

/** The authenticated principal attached by [PolicyEnforcementPoint] for the current call. */
val ApplicationCall.principal: AuthenticatedPrincipal
    get() = attributes[PrincipalKey]

class PolicyEnforcementConfig {
    lateinit var tokenGateway: TokenGateway
    lateinit var action: Action

    /**
     * Resolves the owning customerId of the resource this route addresses (e.g. the order behind
     * `{id}`), given the call and the already-authenticated principal. Null action for a
     * role-only check (no object-level ownership involved). A resolver that returns null means
     * "resource not found" and short-circuits with 404 before any policy decision is made.
     */
    var resourceOwnerId: (suspend (ApplicationCall, AuthenticatedPrincipal) -> String?)? = null

    /**
     * Set when the authorization target can only be known after reading the request body (e.g.
     * creating an order on behalf of a given customerId). In that case this plugin only
     * authenticates; the route handler calls [PolicyDecisionPoint] itself, before performing any
     * write, once it has parsed the body.
     */
    var deferDecisionToHandler: Boolean = false
}

/**
 * Ktor interceptor that resolves the authenticated principal and calls [PolicyDecisionPoint]
 * before the route handler runs — the backend half of the authorization layer described in
 * CLAUDE.md. Failures are reported as domain exceptions and translated to HTTP responses by the
 * StatusPages configuration, not by this plugin.
 */
val PolicyEnforcementPoint = createRouteScopedPlugin(
    name = "PolicyEnforcementPoint",
    createConfiguration = ::PolicyEnforcementConfig,
) {
    val tokenGateway = pluginConfig.tokenGateway
    val action = pluginConfig.action
    val resourceOwnerIdResolver = pluginConfig.resourceOwnerId
    val deferDecisionToHandler = pluginConfig.deferDecisionToHandler

    onCall { call ->
        val token = call.bearerToken()
            ?: throw InvalidOrExpiredTokenException("missing bearer token")
        val principal = tokenGateway.verifyAccessToken(token)
        call.attributes.put(PrincipalKey, principal)

        if (deferDecisionToHandler) return@onCall

        val resourceOwnerId = resourceOwnerIdResolver?.let { resolve ->
            resolve(call, principal) ?: throw ResourceNotFoundException()
        }

        val decision = PolicyDecisionPoint.evaluate(
            role = principal.role,
            action = action,
            requesterId = principal.userId,
            resourceOwnerId = resourceOwnerId,
        )
        if (decision is Decision.Deny) {
            throw ForbiddenException(decision.reason)
        }
    }
}

private fun ApplicationCall.bearerToken(): String? =
    request.header(HttpHeaders.Authorization)
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.removePrefix("Bearer ")
        ?.trim()
