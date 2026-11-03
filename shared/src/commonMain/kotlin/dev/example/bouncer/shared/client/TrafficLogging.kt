package dev.example.bouncer.shared.client

import dev.example.bouncer.shared.dto.TokenPairResponse
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Where the client reports its traffic and session events (Logcat on Android, the browser console
 * on web). The logs deliberately print whole tokens so the demo can show them, which is why each
 * app only supplies a real logger in debug builds; the default drops everything.
 */
fun interface ClientLogger {
    fun log(message: String)

    companion object {
        val None = ClientLogger { }
    }
}

internal class TrafficLoggingConfig {
    var logger: ClientLogger = ClientLogger.None
}

/**
 * Logs every HTTP attempt: method, URL, which bearer token was sent (and how long it has left),
 * then the status and duration. Installed after `Auth`, so the automatic retry after a refresh
 * shows up as its own attempt, right after the `/auth/refresh` call.
 */
internal val TrafficLogging = createClientPlugin("TrafficLogging", ::TrafficLoggingConfig) {
    val logger = pluginConfig.logger

    on(Send) { request ->
        val started = TimeSource.Monotonic.markNow()
        val target = "${request.method.value} ${request.url.buildString()}"
        val bearer = request.headers[HttpHeaders.Authorization]
            ?.removePrefix("Bearer ")
            ?.let { " [access token ${TokenLogDescriptions.describeAccessToken(it)}]" }
            .orEmpty()
        logger.log("--> $target$bearer")
        try {
            val call = proceed(request)
            logger.log("<-- ${call.response.status.value} $target (${started.elapsedNow().inWholeMilliseconds} ms)")
            call
        } catch (e: Throwable) {
            logger.log("<-- FAILED $target (${started.elapsedNow().inWholeMilliseconds} ms): ${e.message}")
            throw e
        }
    }
}

/** Text for the token-related log lines; pure functions so they can be unit-tested. */
internal object TokenLogDescriptions {
    private val json = Json { ignoreUnknownKeys = true }

    /** The token pair as received from the backend: full values, claims and remaining lifetime. */
    fun describeTokenPair(event: String, tokens: TokenPairResponse): String = buildString {
        val now = Clock.System.now().toEpochMilliseconds()
        appendLine("$event: tokens received")
        appendLine("    access token : ${tokens.accessToken}")
        appendLine("                   ${claimsOf(tokens.accessToken) ?: "claims unreadable"}")
        appendLine("                   valid for ${secondsLeft(tokens.accessTokenExpiresAt, now)} s")
        appendLine("    refresh token: ${tokens.refreshToken}")
        append("                   valid for ${secondsLeft(tokens.refreshTokenExpiresAt, now)} s (opaque, rotated on every use)")
    }

    /** Short form for the per-request line: the token's tail plus the seconds it has left (negative = expired). */
    fun describeAccessToken(token: String): String {
        val exp = payloadOf(token)?.get("exp")?.jsonPrimitive?.longOrNull
        val tail = "…${token.takeLast(8)}"
        if (exp == null) return tail
        val left = exp - Clock.System.now().epochSeconds
        return if (left >= 0) "$tail, expires in $left s" else "$tail, EXPIRED ${-left} s ago"
    }

    private fun claimsOf(token: String): String? {
        val payload = payloadOf(token) ?: return null
        fun claim(name: String) = payload[name]?.jsonPrimitive?.content
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull?.let { Instant.fromEpochSeconds(it) }
        return "sub=${claim("sub")} role=${claim("role")} iss=${claim("iss")} exp=$exp"
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun payloadOf(token: String): JsonObject? = runCatching {
        val payload = token.split('.')[1]
        val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(payload)
        json.parseToJsonElement(bytes.decodeToString()).jsonObject
    }.getOrNull()

    private fun secondsLeft(expiresAtEpochMillis: Long, nowEpochMillis: Long): Long =
        (expiresAtEpochMillis - nowEpochMillis) / 1000
}
