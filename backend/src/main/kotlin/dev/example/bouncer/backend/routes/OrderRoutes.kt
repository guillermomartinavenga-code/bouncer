package dev.example.bouncer.backend.routes

import dev.example.bouncer.backend.security.PolicyEnforcementPoint
import dev.example.bouncer.backend.security.principal
import dev.example.bouncer.shared.domain.ForbiddenException
import dev.example.bouncer.shared.domain.InvalidOrderStateException
import dev.example.bouncer.shared.domain.ResourceNotFoundException
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.model.Order
import dev.example.bouncer.shared.domain.model.OrderLine
import dev.example.bouncer.shared.domain.model.OrderStatus
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.policy.Action
import dev.example.bouncer.shared.domain.policy.Decision
import dev.example.bouncer.shared.domain.policy.PolicyDecisionPoint
import dev.example.bouncer.shared.domain.repository.ItemRepository
import dev.example.bouncer.shared.domain.repository.OrderRepository
import dev.example.bouncer.shared.dto.CreateOrderRequest
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

fun Route.orderRoutes(orderRepository: OrderRepository, itemRepository: ItemRepository, tokenGateway: TokenGateway) {
    get("/orders", {
        description = "List orders. CUSTOMER sees only their own; ADMIN/DISPATCHER see all."
    }) {
        val principal = call.principal
        val orders = when (principal.role) {
            Role.CUSTOMER -> orderRepository.findByCustomerId(principal.userId)
            Role.ADMIN, Role.DISPATCHER -> orderRepository.findAll()
        }
        call.respond(orders.map { it.toDto() })
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.LIST_ORDERS
    }

    get("/orders/{id}", {
        description = "Get a single order. CUSTOMER can only fetch their own (BOLA-protected)."
    }) {
        val id = call.parameters["id"]!!
        val order = orderRepository.findById(id) ?: throw ResourceNotFoundException()
        call.respond(order.toDto())
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.VIEW_ORDER_BY_ID
        this.resourceOwnerId = { call, _ -> orderRepository.findById(call.parameters["id"]!!)?.customerId }
    }

    post("/orders", {
        description = "Create an order. CUSTOMER creates their own; ADMIN can create on behalf of any customer."
    }) {
        val principal = call.principal
        val request = call.receive<CreateOrderRequest>()
        val targetCustomerId = request.customerId ?: principal.userId

        // CREATE_ORDER's authorization target is the request body's customerId, which
        // PolicyEnforcementPoint cannot resolve before the body is read — so it only
        // authenticates for this route (deferDecisionToHandler = true) and the decision is made
        // here, before any state change.
        val decision = PolicyDecisionPoint.evaluate(
            role = principal.role,
            action = Action.CREATE_ORDER,
            requesterId = principal.userId,
            resourceOwnerId = targetCustomerId,
        )
        if (decision is Decision.Deny) throw ForbiddenException(decision.reason)

        if (request.lines.isEmpty()) throw InvalidOrderStateException("an order needs at least one line")
        request.lines.forEach { line ->
            itemRepository.findById(line.itemId) ?: throw ResourceNotFoundException("item ${line.itemId} not found")
        }

        val order = Order(
            id = newId("order"),
            customerId = targetCustomerId,
            lines = request.lines.map { OrderLine(it.itemId, it.quantity) },
            status = OrderStatus.PENDING,
            createdAt = System.currentTimeMillis(),
        )
        call.respond(HttpStatusCode.Created, orderRepository.save(order).toDto())
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.CREATE_ORDER
        this.deferDecisionToHandler = true
    }

    patch("/orders/{id}/status", {
        description = "Mark an order as dispatched. DISPATCHER exclusively."
    }) {
        val id = call.parameters["id"]!!
        val order = orderRepository.findById(id) ?: throw ResourceNotFoundException()
        if (order.status != OrderStatus.PENDING) {
            throw InvalidOrderStateException("only a PENDING order can be dispatched")
        }
        val dispatched = order.copy(status = OrderStatus.DISPATCHED)
        call.respond(orderRepository.save(dispatched).toDto())
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.DISPATCH_ORDER
    }

    // Cancellation is a state transition (PENDING -> CANCELLED), not a hard delete, so the order
    // stays visible in order history — DELETE is used here only because that's the verb the
    // approved spec assigns to this action.
    delete("/orders/{id}", {
        description = "Cancel a PENDING order. CUSTOMER: own only. ADMIN: any."
    }) {
        val id = call.parameters["id"]!!
        val order = orderRepository.findById(id) ?: throw ResourceNotFoundException()
        if (order.status != OrderStatus.PENDING) {
            throw InvalidOrderStateException("only a PENDING order can be cancelled")
        }
        orderRepository.save(order.copy(status = OrderStatus.CANCELLED))
        call.respond(HttpStatusCode.NoContent)
    }.install(PolicyEnforcementPoint) {
        this.tokenGateway = tokenGateway
        this.action = Action.CANCEL_ORDER
        this.resourceOwnerId = { call, _ -> orderRepository.findById(call.parameters["id"]!!)?.customerId }
    }
}
