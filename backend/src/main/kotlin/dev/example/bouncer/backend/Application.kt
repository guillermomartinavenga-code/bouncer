package dev.example.bouncer.backend

import dev.example.bouncer.backend.di.backendModule
import dev.example.bouncer.backend.plugins.installStatusPages
import dev.example.bouncer.backend.routes.authRoutes
import dev.example.bouncer.backend.routes.customerRoutes
import dev.example.bouncer.backend.routes.itemRoutes
import dev.example.bouncer.backend.routes.orderRoutes
import dev.example.bouncer.backend.security.JwtTokenGateway
import dev.example.bouncer.shared.domain.auth.TokenGateway
import dev.example.bouncer.shared.domain.repository.ItemRepository
import dev.example.bouncer.shared.domain.repository.OrderRepository
import dev.example.bouncer.shared.domain.repository.UserRepository
import dev.example.bouncer.shared.domain.usecase.AuthenticateUserUseCase
import dev.example.bouncer.shared.domain.usecase.LogoutUseCase
import dev.example.bouncer.shared.domain.usecase.RefreshSessionUseCase
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin

fun Application.module() {
    install(Koin) {
        modules(backendModule)
    }

    install(ContentNegotiation) {
        json()
    }
    install(CallLogging)
    installStatusPages()

    install(OpenApi) {
        info {
            title = "Bouncer API"
            version = "0.1.0"
            description = "Evidence project for RBAC, object-level authorization and short-lived " +
                "tokens. Not production security (see README)."
        }
        outputFormat = OutputFormat.JSON
    }

    val userRepository by inject<UserRepository>()
    val itemRepository by inject<ItemRepository>()
    val orderRepository by inject<OrderRepository>()
    val tokenGateway by inject<TokenGateway>()
    val jwtTokenGateway by inject<JwtTokenGateway>()
    val authenticateUserUseCase by inject<AuthenticateUserUseCase>()
    val refreshSessionUseCase by inject<RefreshSessionUseCase>()
    val logoutUseCase by inject<LogoutUseCase>()

    routing {
        route("/api.json") { openApi() }
        route("/swagger") { swaggerUI("/api.json") }

        authRoutes(authenticateUserUseCase, refreshSessionUseCase, logoutUseCase, jwtTokenGateway, userRepository)
        itemRoutes(itemRepository, tokenGateway)
        orderRoutes(orderRepository, itemRepository, tokenGateway)
        customerRoutes(userRepository, tokenGateway)
    }
}
