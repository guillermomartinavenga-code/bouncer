package dev.example.bouncer.shared.domain.usecase

import dev.example.bouncer.shared.domain.model.ClientType
import dev.example.bouncer.shared.domain.model.Role

/**
 * Login restriction by client_type: the server rejects a login whose user role doesn't match the
 * declared client_type, independently of which screens exist in either client app.
 */
object ClientTypePolicy {
    fun isAllowed(role: Role, clientType: ClientType): Boolean = when (clientType) {
        ClientType.ANDROID -> role == Role.CUSTOMER || role == Role.DISPATCHER
        ClientType.WEB -> role == Role.CUSTOMER || role == Role.ADMIN
    }
}
