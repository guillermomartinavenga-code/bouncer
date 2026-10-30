package dev.example.bouncer.backend.routes

import dev.example.bouncer.shared.domain.auth.TokenPair
import dev.example.bouncer.shared.domain.model.Item
import dev.example.bouncer.shared.domain.model.Order
import dev.example.bouncer.shared.domain.model.User
import dev.example.bouncer.shared.dto.ItemResponse
import dev.example.bouncer.shared.dto.OrderLineDto
import dev.example.bouncer.shared.dto.OrderResponse
import dev.example.bouncer.shared.dto.TokenPairResponse
import dev.example.bouncer.shared.dto.UserResponse
import java.util.UUID

fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

fun TokenPair.toResponse() = TokenPairResponse(
    accessToken = accessToken.value,
    accessTokenExpiresAt = accessToken.expiresAtEpochMillis,
    refreshToken = refreshToken.value,
    refreshTokenExpiresAt = refreshToken.expiresAtEpochMillis,
)

fun User.toSummaryDto() = UserResponse(id = id, name = name, email = email, role = role)

fun Item.toDto() = ItemResponse(id = id, name = name, price = price, stock = stock)

fun Order.toDto() = OrderResponse(
    id = id,
    customerId = customerId,
    lines = lines.map { OrderLineDto(it.itemId, it.quantity) },
    status = status,
    createdAt = createdAt,
)
