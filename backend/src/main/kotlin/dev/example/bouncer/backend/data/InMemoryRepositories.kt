package dev.example.bouncer.backend.data

import dev.example.bouncer.shared.domain.model.Item
import dev.example.bouncer.shared.domain.model.Order
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.model.User
import dev.example.bouncer.shared.domain.repository.ItemRepository
import dev.example.bouncer.shared.domain.repository.OrderRepository
import dev.example.bouncer.shared.domain.repository.UserRepository
import java.util.concurrent.ConcurrentHashMap

class InMemoryUserRepository(initial: List<User>) : UserRepository {
    private val usersById = ConcurrentHashMap(initial.associateBy { it.id })

    override suspend fun findById(id: String): User? = usersById[id]

    override suspend fun findByEmail(email: String): User? =
        usersById.values.find { it.email.equals(email, ignoreCase = true) }

    override suspend fun findAllByRole(role: Role): List<User> =
        usersById.values.filter { it.role == role }
}

class InMemoryItemRepository(initial: List<Item>) : ItemRepository {
    private val items = ConcurrentHashMap(initial.associateBy { it.id })

    override suspend fun findAll(): List<Item> = items.values.toList()

    override suspend fun findById(id: String): Item? = items[id]

    override suspend fun save(item: Item): Item {
        items[item.id] = item
        return item
    }

    override suspend fun delete(id: String): Boolean = items.remove(id) != null
}

class InMemoryOrderRepository(initial: List<Order>) : OrderRepository {
    private val orders = ConcurrentHashMap(initial.associateBy { it.id })

    override suspend fun findAll(): List<Order> = orders.values.toList()

    override suspend fun findByCustomerId(customerId: String): List<Order> =
        orders.values.filter { it.customerId == customerId }

    override suspend fun findById(id: String): Order? = orders[id]

    override suspend fun save(order: Order): Order {
        orders[order.id] = order
        return order
    }

    override suspend fun delete(id: String): Boolean = orders.remove(id) != null
}
