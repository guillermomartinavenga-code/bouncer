package dev.example.bouncer.backend.routes

import dev.example.bouncer.backend.security.PolicyEnforcementPoint
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.policy.Action
import dev.example.bouncer.shared.domain.repository.UserRepository
import io.github.smiley4.ktoropenapi.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.customerRoutes(userRepository: UserRepository, tokenGateway: TokenGateway) {
    get("/customers", {
        description = "List all customers. ADMIN only. Their orders are available via GET /orders."
    }) {
        call.respond(userRepository.findAllByRole(Role.CUSTOMER).map { it.toSummaryDto() })
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.LIST_CUSTOMERS
    }
}
