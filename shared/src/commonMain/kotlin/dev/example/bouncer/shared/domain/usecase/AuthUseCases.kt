package dev.example.bouncer.shared.domain.usecase

import dev.example.bouncer.shared.domain.ClientTypeNotAllowedException
import dev.example.bouncer.shared.domain.InvalidCredentialsException
import dev.example.bouncer.shared.domain.auth.PasswordHasher
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.auth.TokenPair
import dev.example.bouncer.shared.domain.model.ClientType
import dev.example.bouncer.shared.domain.repository.UserRepository

class AuthenticateUserUseCase(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenGateway: TokenGateway,
) {
    suspend operator fun invoke(email: String, password: String, clientType: ClientType): TokenPair {
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordHasher.verify(password, user.passwordHash)) throw InvalidCredentialsException()
        if (!ClientTypePolicy.isAllowed(user.role, clientType)) {
            throw ClientTypeNotAllowedException(user.role, clientType)
        }
        return tokenGateway.issueTokenPair(user)
    }
}

class RefreshSessionUseCase(private val tokenGateway: TokenGateway) {
    suspend operator fun invoke(refreshToken: String): TokenPair = tokenGateway.rotateRefreshToken(refreshToken)
}

class LogoutUseCase(private val tokenGateway: TokenGateway) {
    suspend operator fun invoke(refreshToken: String) = tokenGateway.revokeRefreshToken(refreshToken)
}
