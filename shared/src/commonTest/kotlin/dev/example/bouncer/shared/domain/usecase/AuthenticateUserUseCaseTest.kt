package dev.example.bouncer.shared.domain.usecase

import dev.example.bouncer.shared.domain.ClientTypeNotAllowedException
import dev.example.bouncer.shared.domain.InvalidCredentialsException
import dev.example.bouncer.shared.domain.auth.AccessToken
import dev.example.bouncer.shared.domain.auth.AuthenticatedPrincipal
import dev.example.bouncer.shared.domain.auth.PasswordHasher
import dev.example.bouncer.shared.domain.auth.RefreshToken
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.auth.TokenPair
import dev.example.bouncer.shared.domain.model.ClientType
import dev.example.bouncer.shared.domain.model.Role
import dev.example.bouncer.shared.domain.model.User
import dev.example.bouncer.shared.domain.repository.UserRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Login must be rejected when the user's role doesn't match the declared client_type, regardless
 * of correct credentials — this is the second of the two most sensitive authorization rules.
 */
class AuthenticateUserUseCaseTest {

    private val customer = User("customer-1", "Ana", "ana@example.com", "hash", Role.CUSTOMER)
    private val dispatcher = User("dispatcher-1", "Dani", "dani@example.com", "hash", Role.DISPATCHER)
    private val admin = User("admin-1", "Caro", "caro@example.com", "hash", Role.ADMIN)

    private fun useCase(users: List<User>) = AuthenticateUserUseCase(
        userRepository = FakeUserRepository(users),
        passwordHasher = FakePasswordHasher(),
        tokenGateway = FakeTokenGateway(),
    )

    @Test
    fun `customer can log in from android`() = runTest {
        val result = useCase(listOf(customer))(customer.email, "correct", ClientType.ANDROID)
        assertTrue(result.accessToken.value.isNotBlank())
    }

    @Test
    fun `customer can log in from web`() = runTest {
        useCase(listOf(customer))(customer.email, "correct", ClientType.WEB)
    }

    @Test
    fun `dispatcher can log in from android`() = runTest {
        useCase(listOf(dispatcher))(dispatcher.email, "correct", ClientType.ANDROID)
    }

    @Test
    fun `dispatcher cannot log in from web`() = runTest {
        assertFailsWith<ClientTypeNotAllowedException> {
            useCase(listOf(dispatcher))(dispatcher.email, "correct", ClientType.WEB)
        }
    }

    @Test
    fun `admin can log in from web`() = runTest {
        useCase(listOf(admin))(admin.email, "correct", ClientType.WEB)
    }

    @Test
    fun `admin cannot log in from android`() = runTest {
        assertFailsWith<ClientTypeNotAllowedException> {
            useCase(listOf(admin))(admin.email, "correct", ClientType.ANDROID)
        }
    }

    @Test
    fun `wrong password is rejected before client_type is even considered`() = runTest {
        assertFailsWith<InvalidCredentialsException> {
            useCase(listOf(admin))(admin.email, "wrong", ClientType.ANDROID)
        }
    }

    @Test
    fun `unknown email is rejected`() = runTest {
        assertFailsWith<InvalidCredentialsException> {
            useCase(emptyList())("nobody@example.com", "whatever", ClientType.ANDROID)
        }
    }

    private class FakeUserRepository(private val users: List<User>) : UserRepository {
        override suspend fun findById(id: String) = users.find { it.id == id }
        override suspend fun findByEmail(email: String) = users.find { it.email == email }
        override suspend fun findAllByRole(role: Role) = users.filter { it.role == role }
    }

    private class FakePasswordHasher : PasswordHasher {
        override fun hash(plainPassword: String) = plainPassword
        override fun verify(plainPassword: String, hash: String) = plainPassword == "correct"
    }

    private class FakeTokenGateway : TokenGateway {
        override suspend fun issueTokenPair(user: User) = TokenPair(
            accessToken = AccessToken("fake-access-${user.id}", 0L),
            refreshToken = RefreshToken("fake-refresh-${user.id}", 0L),
        )

        override suspend fun rotateRefreshToken(refreshToken: String): TokenPair =
            error("not used by this test")

        override suspend fun revokeRefreshToken(refreshToken: String): Unit =
            error("not used by this test")

        override suspend fun verifyAccessToken(accessToken: String): AuthenticatedPrincipal =
            error("not used by this test")
    }
}
