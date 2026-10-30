package dev.example.bouncer.shared.domain

import dev.example.bouncer.shared.domain.model.ClientType
import dev.example.bouncer.shared.domain.model.Role

sealed class DomainException(message: String) : Exception(message)

class InvalidCredentialsException : DomainException("Invalid email or password")

class ClientTypeNotAllowedException(role: Role, clientType: ClientType) :
    DomainException("Role $role cannot authenticate from client_type ${clientType.name.lowercase()}")

class InvalidOrExpiredTokenException(message: String = "Invalid or expired token") : DomainException(message)

class ForbiddenException(message: String = "Forbidden") : DomainException(message)

class ResourceNotFoundException(message: String = "Resource not found") : DomainException(message)

class InvalidOrderStateException(message: String) : DomainException(message)
