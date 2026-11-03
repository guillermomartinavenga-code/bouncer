package dev.example.bouncer.backend

import dev.example.bouncer.backend.di.backendModule
import dev.example.bouncer.backend.plugins.installStatusPages
import dev.example.bouncer.backend.routes.authRoutes
import dev.example.bouncer.backend.routes.customerRoutes
import dev.example.bouncer.backend.routes.itemRoutes
import dev.example.bouncer.backend.routes.orderRoutes
import dev.example.bouncer.backend.security.JwtTokenGateway
import dev.example.bouncer.backend.security.principalOrNull
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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
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
    install(CallLogging) {
        // One line per request: status, duration and who made it (the principal is only known on
        // routes behind the PolicyEnforcementPoint, and only if the access token verified).
        format { call ->
            val principal = call.principalOrNull
            val who = if (principal == null) "anonymous" else "user=${principal.userId} role=${principal.role}"
            "${call.response.status()?.value} ${call.request.httpMethod.value} ${call.request.uri} " +
                "(${call.processingTimeMillis()} ms) $who"
        }
    }
    install(CORS) {
        // The browser web app (dev server on :8081) calls this backend cross-origin. Only that dev
        // origin is allowed; the Android app isn't a browser, so CORS doesn't apply to it.
        allowHost("localhost:8081", schemes = listOf("http"))
        allowHost("127.0.0.1:8081", schemes = listOf("http"))
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
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
