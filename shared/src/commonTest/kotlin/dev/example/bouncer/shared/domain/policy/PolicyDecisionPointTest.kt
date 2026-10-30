package dev.example.bouncer.shared.domain.policy

import dev.example.bouncer.shared.domain.model.Role
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Object-level authorization (BOLA) coverage: a CUSTOMER must never be able to act on another
 * customer's order, even when they know its id — regardless of which action they attempt.
 */
class PolicyDecisionPointTest {

    @Test
    fun `customer can view their own order`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.CUSTOMER,
            action = Action.VIEW_ORDER_BY_ID,
            requesterId = "customer-1",
            resourceOwnerId = "customer-1",
        )
        assertIs<Decision.Allow>(decision)
    }

    @Test
    fun `customer cannot view another customer's order`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.CUSTOMER,
            action = Action.VIEW_ORDER_BY_ID,
            requesterId = "customer-1",
            resourceOwnerId = "customer-2",
        )
        assertIs<Decision.Deny>(decision)
    }

    @Test
    fun `customer cannot cancel another customer's order`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.CUSTOMER,
            action = Action.CANCEL_ORDER,
            requesterId = "customer-1",
            resourceOwnerId = "customer-2",
        )
        assertIs<Decision.Deny>(decision)
    }

    @Test
    fun `customer can cancel their own order`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.CUSTOMER,
            action = Action.CANCEL_ORDER,
            requesterId = "customer-1",
            resourceOwnerId = "customer-1",
        )
        assertIs<Decision.Allow>(decision)
    }

    @Test
    fun `customer cannot create an order for another customer`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.CUSTOMER,
            action = Action.CREATE_ORDER,
            requesterId = "customer-1",
            resourceOwnerId = "customer-2",
        )
        assertIs<Decision.Deny>(decision)
    }

    @Test
    fun `admin can view any order regardless of ownership`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.ADMIN,
            action = Action.VIEW_ORDER_BY_ID,
            requesterId = "admin-1",
            resourceOwnerId = "customer-2",
        )
        assertIs<Decision.Allow>(decision)
    }

    @Test
    fun `dispatcher can view any order regardless of ownership`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.DISPATCHER,
            action = Action.VIEW_ORDER_BY_ID,
            requesterId = "dispatcher-1",
            resourceOwnerId = "customer-2",
        )
        assertIs<Decision.Allow>(decision)
    }

    @Test
    fun `dispatcher cannot cancel orders even their own id coincidentally matches`() {
        val decision = PolicyDecisionPoint.evaluate(
            role = Role.DISPATCHER,
            action = Action.CANCEL_ORDER,
            requesterId = "dispatcher-1",
            resourceOwnerId = "dispatcher-1",
        )
        assertIs<Decision.Deny>(decision)
    }

    @Test
    fun `only dispatcher can dispatch an order`() {
        assertIs<Decision.Allow>(
            PolicyDecisionPoint.evaluate(Role.DISPATCHER, Action.DISPATCH_ORDER, "dispatcher-1"),
        )
        assertIs<Decision.Deny>(
            PolicyDecisionPoint.evaluate(Role.ADMIN, Action.DISPATCH_ORDER, "admin-1"),
        )
        assertIs<Decision.Deny>(
            PolicyDecisionPoint.evaluate(Role.CUSTOMER, Action.DISPATCH_ORDER, "customer-1"),
        )
    }
}
