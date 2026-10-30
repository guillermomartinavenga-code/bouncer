package dev.example.bouncer.shared.dto

import dev.example.bouncer.shared.domain.model.OrderStatus
import dev.example.bouncer.shared.domain.model.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("client_type") val clientType: String,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class TokenPairResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    val refreshToken: String,
    val refreshTokenExpiresAt: Long,
)

@Serializable
data class UserResponse(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
)

@Serializable
data class ItemResponse(
    val id: String,
    val name: String,
    val price: Double,
    val stock: Int,
)

@Serializable
data class ItemWriteRequest(
    val name: String,
    val price: Double,
    val stock: Int,
)

@Serializable
data class OrderLineDto(
    val itemId: String,
    val quantity: Int,
)

@Serializable
data class OrderResponse(
    val id: String,
    val customerId: String,
    val lines: List<OrderLineDto>,
    val status: OrderStatus,
    val createdAt: Long,
)

@Serializable
data class CreateOrderRequest(
    val customerId: String? = null,
    val lines: List<OrderLineDto>,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)
