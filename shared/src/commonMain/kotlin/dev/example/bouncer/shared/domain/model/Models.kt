package dev.example.bouncer.shared.domain.model

enum class Role { CUSTOMER, DISPATCHER, ADMIN }

enum class ClientType { ANDROID, WEB }

enum class OrderStatus { PENDING, DISPATCHED, CANCELLED }

data class User(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val role: Role,
)

data class Item(
    val id: String,
    val name: String,
    val price: Double,
    val stock: Int,
)

data class OrderLine(
    val itemId: String,
    val quantity: Int,
)

data class Order(
    val id: String,
    val customerId: String,
    val lines: List<OrderLine>,
    val status: OrderStatus,
    val createdAt: Long,
)
