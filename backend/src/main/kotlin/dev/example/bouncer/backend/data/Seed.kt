package dev.example.bouncer.backend.data

import dev.example.bouncer.shared.domain.auth.PasswordHasher
import dev.example.bouncer.shared.domain.model.Item
import dev.example.bouncer.shared.domain.model.Order
import dev.example.bouncer.shared.domain.model.OrderLine
import dev.example.bouncer.shared.domain.model.OrderStatus
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.model.User

data class SeedData(val users: List<User>, val items: List<Item>, val orders: List<Order>)

/** Same password for every seeded user, for demo convenience only — documented in the README. */
const val SEED_DEMO_PASSWORD = "password123"

fun buildSeedData(passwordHasher: PasswordHasher): SeedData {
    val passwordHash = passwordHasher.hash(SEED_DEMO_PASSWORD)

    val customer1 = User("customer-1", "Ana Customer", "ana@example.com", passwordHash, Role.CUSTOMER)
    val customer2 = User("customer-2", "Beto Customer", "beto@example.com", passwordHash, Role.CUSTOMER)
    val dispatcher = User("dispatcher-1", "Dani Dispatcher", "dani@example.com", passwordHash, Role.DISPATCHER)
    val admin = User("admin-1", "Caro Admin", "caro@example.com", passwordHash, Role.ADMIN)
    val users = listOf(customer1, customer2, dispatcher, admin)

    val items = listOf(
        Item("item-1", "Widget", 9.99, 100),
        Item("item-2", "Gadget", 19.99, 50),
        Item("item-3", "Gizmo", 5.50, 200),
        Item("item-4", "Doohickey", 12.00, 30),
        Item("item-5", "Thingamajig", 3.25, 500),
    )

    val now = System.currentTimeMillis()
    val orders = listOf(
        Order("order-1", customer1.id, listOf(OrderLine("item-1", 2)), OrderStatus.PENDING, now - 60_000),
        Order("order-2", customer1.id, listOf(OrderLine("item-2", 1)), OrderStatus.DISPATCHED, now - 120_000),
        Order("order-3", customer1.id, listOf(OrderLine("item-3", 1)), OrderStatus.CANCELLED, now - 180_000),
        Order("order-4", customer2.id, listOf(OrderLine("item-1", 1), OrderLine("item-4", 1)), OrderStatus.PENDING, now - 90_000),
        Order("order-5", customer2.id, listOf(OrderLine("item-5", 3)), OrderStatus.DISPATCHED, now - 150_000),
    )

    return SeedData(users, items, orders)
}
