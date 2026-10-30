package dev.example.bouncer.shared.domain.repository

import dev.example.bouncer.shared.domain.model.Item
import dev.example.bouncer.shared.domain.model.Order
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.model.User

interface UserRepository {
    suspend fun findById(id: String): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findAllByRole(role: Role): List<User>
}

interface ItemRepository {
    suspend fun findAll(): List<Item>
    suspend fun findById(id: String): Item?
    suspend fun save(item: Item): Item
    suspend fun delete(id: String): Boolean
}

interface OrderRepository {
    suspend fun findAll(): List<Order>
    suspend fun findByCustomerId(customerId: String): List<Order>
    suspend fun findById(id: String): Order?
    suspend fun save(order: Order): Order
    suspend fun delete(id: String): Boolean
}
