package dev.example.bouncer.backend.plugins

import dev.example.bouncer.shared.common.ErrorType
import dev.example.bouncer.shared.domain.ClientTypeNotAllowedException
import dev.example.bouncer.shared.domain.ForbiddenException
import dev.example.bouncer.shared.domain.InvalidCredentialsException
import dev.example.bouncer.shared.domain.InvalidOrExpiredTokenException
import dev.example.bouncer.shared.domain.InvalidOrderStateException
import dev.example.bouncer.shared.domain.ResourceNotFoundException
import dev.example.bouncer.shared.dto.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<InvalidCredentialsException> { call, cause ->
            call.respondError(HttpStatusCode.Unauthorized, ErrorType.UNAUTHORIZED, cause.message)
        }
        exception<ClientTypeNotAllowedException> { call, cause ->
            call.respondError(HttpStatusCode.Forbidden, ErrorType.FORBIDDEN, cause.message)
        }
        exception<InvalidOrExpiredTokenException> { call, cause ->
            call.respondError(HttpStatusCode.Unauthorized, ErrorType.UNAUTHORIZED, cause.message)
        }
        exception<ForbiddenException> { call, cause ->
            call.respondError(HttpStatusCode.Forbidden, ErrorType.FORBIDDEN, cause.message)
        }
        exception<ResourceNotFoundException> { call, cause ->
            call.respondError(HttpStatusCode.NotFound, ErrorType.UNKNOWN, cause.message)
        }
        exception<InvalidOrderStateException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, ErrorType.UNKNOWN, cause.message)
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, ErrorType.UNKNOWN, cause.message)
        }
        exception<BadRequestException> { call, cause ->
            call.respondError(HttpStatusCode.BadRequest, ErrorType.UNKNOWN, cause.message)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respondError(HttpStatusCode.InternalServerError, ErrorType.UNKNOWN, "internal server error")
        }
    }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, type: ErrorType, message: String?) {
    // The access log only has the status; this adds why the request was rejected (e.g. which rule denied it).
    application.log.info("rejected {} {}: {} {}", request.httpMethod.value, request.uri, type, message ?: status.description)
    respond(status, ErrorResponse(type.name, message ?: status.description))
}
