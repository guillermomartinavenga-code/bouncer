package dev.example.bouncer.shared.domain.policy

import dev.example.bouncer.shared.domain.model.Role

/**
 * Business actions the backend exposes. One value per protected operation in the role/permission
 * matrix (see CLAUDE.md) — never a generic "read"/"write".
 */
enum class Action {
    VIEW_ITEMS,
    MANAGE_ITEMS,
    LIST_ORDERS,
    VIEW_ORDER_BY_ID,
    CREATE_ORDER,
    DISPATCH_ORDER,
    CANCEL_ORDER,
    VIEW_SELF,
    LIST_CUSTOMERS,
}

sealed interface Decision {
    data object Allow : Decision
    data class Deny(val reason: String) : Decision
}

/**
 * Pure authorization engine: role + action + (optional) resource owner -> allow/deny. No I/O, no
 * framework dependency, so it is testable in isolation and is the single place that encodes the
 * role/permission matrix and the object-level authorization rule (BOLA prevention).
 */
object PolicyDecisionPoint {

    fun evaluate(
        role: Role,
        action: Action,
        requesterId: String,
        resourceOwnerId: String? = null,
    ): Decision = when (action) {
        Action.VIEW_ITEMS ->
            Decision.Allow

        Action.MANAGE_ITEMS ->
            allowIf(role == Role.ADMIN, "only ADMIN can manage items")

        // GET /orders is reachable by every role; the handler scopes the result set (own orders
        // for CUSTOMER, all orders for ADMIN/DISPATCHER) — there is no role this denies outright.
        Action.LIST_ORDERS ->
            Decision.Allow

        Action.VIEW_ORDER_BY_ID -> when (role) {
            Role.ADMIN, Role.DISPATCHER -> Decision.Allow
            Role.CUSTOMER -> allowIf(
                requesterId == resourceOwnerId,
                "a CUSTOMER can only view their own orders",
            )
        }

        // resourceOwnerId here is the order's *target* customerId (self for CUSTOMER, whoever the
        // ADMIN names), not an existing resource's owner — the same ownership check works for both.
        Action.CREATE_ORDER -> when (role) {
            Role.CUSTOMER -> allowIf(
                requesterId == resourceOwnerId,
                "a CUSTOMER can only create orders for themselves",
            )
            Role.ADMIN -> Decision.Allow
            Role.DISPATCHER -> Decision.Deny("DISPATCHER cannot create orders")
        }

        Action.DISPATCH_ORDER ->
            allowIf(role == Role.DISPATCHER, "only DISPATCHER can mark an order as dispatched")

        Action.CANCEL_ORDER -> when (role) {
            Role.ADMIN -> Decision.Allow
            Role.CUSTOMER -> allowIf(
                requesterId == resourceOwnerId,
                "a CUSTOMER can only cancel their own orders",
            )
            Role.DISPATCHER -> Decision.Deny("DISPATCHER cannot cancel orders")
        }

        Action.VIEW_SELF ->
            Decision.Allow

        Action.LIST_CUSTOMERS ->
            allowIf(role == Role.ADMIN, "only ADMIN can list customers")
    }

    private fun allowIf(condition: Boolean, reasonIfDenied: String): Decision =
        if (condition) Decision.Allow else Decision.Deny(reasonIfDenied)
}
