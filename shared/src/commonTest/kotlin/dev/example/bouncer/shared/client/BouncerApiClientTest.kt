package dev.example.bouncer.shared.client

import dev.example.bouncer.shared.common.ErrorType
import dev.example.bouncer.shared.dto.ItemResponse
import dev.example.bouncer.shared.presentation.UiState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Verifies the LCE/token-refresh contract from CLAUDE.md: a 401 from access-token expiry is
 * retried transparently and never reaches the caller as an error; a 401 that still fails after
 * refreshing, and a 403, both surface as `UiState.Error`.
 */
class BouncerApiClientTest {

    private val jsonContentType = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `expired access token is refreshed and the request is retried transparently`() = runTest {
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.saveTokens("expired-access", "valid-refresh")
        var itemsCallCount = 0

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/items" -> {
                    itemsCallCount++
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer expired-access") {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        respond(
                            """[{"id":"item-1","name":"Widget","price":9.99,"stock":10}]""",
                            HttpStatusCode.OK,
                            jsonContentType,
                        )
                    }
                }
                "/auth/refresh" -> respond(
                    """{"accessToken":"new-access","accessTokenExpiresAt":0,"refreshToken":"new-refresh","refreshTokenExpiresAt":0}""",
                    HttpStatusCode.OK,
                    jsonContentType,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = BouncerApiClient("http://test", tokenStorage, engine)
        val result = client.getItems()

        assertIs<UiState.Content<List<ItemResponse>>>(result)
        assertEquals(1, result.data.size)
        assertEquals(2, itemsCallCount, "expected one failed call plus one retried call")
        assertEquals("new-access", tokenStorage.getAccessToken())
    }

    @Test
    fun `a refresh token that also fails surfaces the original 401 as UiState Error`() = runTest {
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.saveTokens("expired-access", "also-expired-refresh")

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/items" -> respond("", HttpStatusCode.Unauthorized)
                "/auth/refresh" -> respond(
                    """{"error":"UNAUTHORIZED","message":"refresh token expired"}""",
                    HttpStatusCode.Unauthorized,
                    jsonContentType,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = BouncerApiClient("http://test", tokenStorage, engine)
        val result = client.getItems()

        assertIs<UiState.Error>(result)
        assertEquals(ErrorType.UNAUTHORIZED, result.type)
    }

    @Test
    fun `forbidden response maps directly to UiState Error without attempting a refresh`() = runTest {
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.saveTokens("valid-access", "valid-refresh")
        var refreshCalled = false

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/orders/order-4" -> respond(
                    """{"error":"FORBIDDEN","message":"a CUSTOMER can only view their own orders"}""",
                    HttpStatusCode.Forbidden,
                    jsonContentType,
                )
                "/auth/refresh" -> {
                    refreshCalled = true
                    respond("", HttpStatusCode.Unauthorized)
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val client = BouncerApiClient("http://test", tokenStorage, engine)
        val result = client.getOrder("order-4")

        assertIs<UiState.Error>(result)
        assertEquals(ErrorType.FORBIDDEN, result.type)
        assertFalse(refreshCalled)
    }

    @Test
    fun `logger reports the rejected attempt, the refresh and the retry in order`() = runTest {
        val tokenStorage = InMemoryTokenStorage()
        tokenStorage.saveTokens("expired-access", "valid-refresh")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/items" ->
                    if (request.headers[HttpHeaders.Authorization] == "Bearer expired-access") {
                        respond("", HttpStatusCode.Unauthorized)
                    } else {
                        respond("[]", HttpStatusCode.OK, jsonContentType)
                    }
                "/auth/refresh" -> respond(
                    """{"accessToken":"new-access","accessTokenExpiresAt":0,"refreshToken":"new-refresh","refreshTokenExpiresAt":0}""",
                    HttpStatusCode.OK,
                    jsonContentType,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val lines = mutableListOf<String>()

        BouncerApiClient("http://test", tokenStorage, engine, ClientLogger { lines += it }).getItems()

        val expectedInOrder = listOf(
            "--> GET http://test/items",
            "<-- 401 GET http://test/items",
            "session: access token expired or rejected (401), refreshing with refresh token valid-refresh",
            "--> POST http://test/auth/refresh",
            "<-- 200 POST http://test/auth/refresh",
            "refresh: tokens received",
            "--> GET http://test/items",
            "<-- 200 GET http://test/items",
        )
        var from = 0
        for (expected in expectedInOrder) {
            val found = lines.drop(from).indexOfFirst { it.startsWith(expected) }
            assertTrue(found >= 0, "missing or out of order: '$expected' in $lines")
            from += found + 1
        }
        assertTrue(lines.any { it.contains("new-access") && it.contains("new-refresh") }, "tokens received should be printed")
    }
}
