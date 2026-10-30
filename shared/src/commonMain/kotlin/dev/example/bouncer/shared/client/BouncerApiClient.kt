package dev.example.bouncer.shared.client

import dev.example.bouncer.shared.common.ErrorType
import dev.example.bouncer.shared.dto.CreateOrderRequest
import dev.example.bouncer.shared.dto.ErrorResponse
import dev.example.bouncer.shared.dto.ItemResponse
import dev.example.bouncer.shared.dto.ItemWriteRequest
import dev.example.bouncer.shared.dto.LoginRequest
import dev.example.bouncer.shared.dto.LogoutRequest
import dev.example.bouncer.shared.dto.OrderResponse
import dev.example.bouncer.shared.dto.RefreshRequest
import dev.example.bouncer.shared.dto.TokenPairResponse
import dev.example.bouncer.shared.dto.UserResponse
import dev.example.bouncer.shared.presentation.UiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * The shared HTTP client: every Android/Web screen talks to the backend only through this class.
 * A 401 caused by access-token expiry is retried transparently by Ktor's `Auth` plugin (it calls
 * `/auth/refresh` and retries once); if that retry also fails — refresh token also expired or
 * revoked — the original 401 is returned here and mapped to `UiState.Error(UNAUTHORIZED)`.
 */
class BouncerApiClient(
    private val baseUrl: String,
    private val tokenStorage: TokenStorage,
    engine: HttpClientEngine,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokenStorage.getAccessToken()
                    val refresh = tokenStorage.getRefreshToken()
                    if (access != null && refresh != null) BearerTokens(access, refresh) else null
                }
                refreshTokens {
                    val refreshToken = tokenStorage.getRefreshToken() ?: return@refreshTokens null
                    val response = client.post("$baseUrl/auth/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refreshToken))
                    }
                    if (response.status.isSuccess()) {
                        val tokens = response.body<TokenPairResponse>()
                        tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
                        BearerTokens(tokens.accessToken, tokens.refreshToken)
                    } else {
                        tokenStorage.clear()
                        null
                    }
                }
                sendWithoutRequest { request ->
                    AUTH_EXEMPT_PATHS.none { request.url.encodedPath.endsWith(it) }
                }
            }
        }
    }

    suspend fun login(email: String, password: String, clientType: String): UiState<TokenPairResponse> {
        val result = safeCall<TokenPairResponse> {
            httpClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password, clientType))
            }
        }
        if (result is UiState.Content) {
            tokenStorage.saveTokens(result.data.accessToken, result.data.refreshToken)
            forgetCachedBearerTokens()
        }
        return result
    }

    suspend fun logout(): UiState<Unit> {
        val refreshToken = tokenStorage.getRefreshToken()
        val result = if (refreshToken == null) {
            UiState.Content(Unit)
        } else {
            safeCall<Unit> {
                httpClient.post("$baseUrl/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(LogoutRequest(refreshToken))
                }
            }
        }
        clearLocalSession()
        return result
    }

    /** Drops the stored tokens without telling the server (e.g. the session already expired). */
    suspend fun clearLocalSession() {
        tokenStorage.clear()
        forgetCachedBearerTokens()
    }

    /**
     * Exchanges the stored refresh token for a fresh token pair, e.g. after the Android app
     * unlocks its saved session. A rejected refresh token also clears the local session.
     */
    suspend fun restoreSession(): UiState<Unit> {
        val refreshToken = tokenStorage.getRefreshToken()
            ?: return UiState.Error(ErrorType.UNAUTHORIZED, "no saved session")
        return when (val result = safeCall<TokenPairResponse> {
            httpClient.post("$baseUrl/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }
        }) {
            is UiState.Content -> {
                tokenStorage.saveTokens(result.data.accessToken, result.data.refreshToken)
                forgetCachedBearerTokens()
                UiState.Content(Unit)
            }
            is UiState.Error -> {
                if (result.type == ErrorType.UNAUTHORIZED) clearLocalSession()
                result
            }
            UiState.Loading -> UiState.Loading
        }
    }

    suspend fun me(): UiState<UserResponse> = safeCall { httpClient.get("$baseUrl/me") }

    suspend fun getItems(): UiState<List<ItemResponse>> = safeCall { httpClient.get("$baseUrl/items") }

    suspend fun createItem(request: ItemWriteRequest): UiState<ItemResponse> = safeCall {
        httpClient.post("$baseUrl/items") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateItem(id: String, request: ItemWriteRequest): UiState<ItemResponse> = safeCall {
        httpClient.put("$baseUrl/items/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteItem(id: String): UiState<Unit> = safeCall { httpClient.delete("$baseUrl/items/$id") }

    suspend fun getOrders(): UiState<List<OrderResponse>> = safeCall { httpClient.get("$baseUrl/orders") }

    suspend fun getOrder(id: String): UiState<OrderResponse> = safeCall { httpClient.get("$baseUrl/orders/$id") }

    suspend fun createOrder(request: CreateOrderRequest): UiState<OrderResponse> = safeCall {
        httpClient.post("$baseUrl/orders") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun dispatchOrder(id: String): UiState<OrderResponse> = safeCall {
        httpClient.patch("$baseUrl/orders/$id/status")
    }

    suspend fun cancelOrder(id: String): UiState<Unit> = safeCall { httpClient.delete("$baseUrl/orders/$id") }

    suspend fun getCustomers(): UiState<List<UserResponse>> = safeCall { httpClient.get("$baseUrl/customers") }

    private fun forgetCachedBearerTokens() {
        httpClient.authProvider<BearerAuthProvider>()?.clearToken()
    }

    private suspend inline fun <reified T> safeCall(crossinline request: suspend () -> HttpResponse): UiState<T> =
        try {
            request().toUiState()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UiState.Error(ErrorType.NETWORK, e.message ?: "network error")
        }

    private suspend inline fun <reified T> HttpResponse.toUiState(): UiState<T> = when {
        status.isSuccess() -> UiState.Content(if (T::class == Unit::class) Unit as T else body())
        status == HttpStatusCode.Unauthorized -> UiState.Error(ErrorType.UNAUTHORIZED, errorMessage())
        status == HttpStatusCode.Forbidden -> UiState.Error(ErrorType.FORBIDDEN, errorMessage())
        else -> UiState.Error(ErrorType.UNKNOWN, errorMessage())
    }

    private suspend fun HttpResponse.errorMessage(): String =
        runCatching { body<ErrorResponse>().message }.getOrElse { "request failed with ${status.value}" }

    companion object {
        private val AUTH_EXEMPT_PATHS = listOf("/auth/login", "/auth/refresh", "/auth/logout", "/jwks.json")
    }
}
