package dev.jacobandersen.bastion.micropub.type.resp

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import tools.jackson.databind.ObjectMapper
import java.net.URI

sealed interface ApiResponse<out T> {
    sealed interface Success<out T> : ApiResponse<T> {
        data class Ok(
            val data: Any,
        ) : Success<Any>

        data class Created(
            val location: String,
        ) : Success<Nothing>

        data class Accepted(
            val location: String,
        ) : Success<Nothing>

        data object NoContent : Success<Nothing>
    }

    sealed interface Error :
        ApiResponse<Nothing>,
        CommonError {
        val status: HttpStatus

        data class Forbidden(
            override val error: String = "forbidden",
            override val errorDescription: String = "You do not have permission to access this resource",
        ) : Error {
            override val status = HttpStatus.FORBIDDEN
        }

        data class InsufficientScope(
            override val error: String = "insufficient_scope",
            override val errorDescription: String = "Your access token lacks the scope required for this resource",
        ) : Error {
            override val status = HttpStatus.FORBIDDEN
        }

        data class Unauthorized(
            override val error: String = "unauthorized",
            override val errorDescription: String = "An access token is required to access this resource",
        ) : Error {
            override val status = HttpStatus.UNAUTHORIZED
        }

        data class InvalidRequest(
            override val error: String = "invalid_request",
            override val errorDescription: String = "One or more parameters are missing or are malformed",
        ) : Error {
            override val status = HttpStatus.BAD_REQUEST
        }

        data class NotFound(
            override val error: String = "not_found",
            override val errorDescription: String = "Not found",
        ) : Error {
            override val status = HttpStatus.NOT_FOUND
        }

        data class Unknown(
            override val error: String = "unknown",
            override val errorDescription: String = "Unknown internal server error",
        ) : Error {
            override val status = HttpStatus.INTERNAL_SERVER_ERROR
        }

        data class Gone(
            override val error: String = "gone",
            override val errorDescription: String = "Resource has been deleted",
        ) : Error {
            override val status = HttpStatus.GONE
        }
    }
}

fun <T> ApiResponse<T>.toResponseEntity(): ResponseEntity<*> =
    when (this) {
        is ApiResponse.Success.Ok -> {
            ResponseEntity.ok(this.data)
        }

        is ApiResponse.Success.Created -> {
            ResponseEntity
                .created(URI(this.location))
                .build<Void>()
        }

        is ApiResponse.Success.Accepted -> {
            ResponseEntity
                .accepted()
                .header(HttpHeaders.LOCATION, this.location)
                .build<Void>()
        }

        is ApiResponse.Success.NoContent -> {
            ResponseEntity.noContent().build<Void>()
        }

        is ApiResponse.Error -> {
            val response = ResponseEntity.status(this.status)
            if (this is ApiResponse.Error.Unauthorized) {
                response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            }
            response.body(
                mapOf(
                    "error" to this.error,
                    "error_description" to this.errorDescription,
                ),
            )
        }
    }

fun ResponseEntity<*>.writeResponse(
    resp: HttpServletResponse,
    objectMapper: ObjectMapper,
) {
    resp.status = this.statusCode.value()
    this.headers.forEach { key, values -> values.forEach { resp.addHeader(key, it) } }
    resp.contentType = MediaType.APPLICATION_JSON_VALUE
    if (this.hasBody()) {
        objectMapper.writeValue(resp.writer, this.body)
    }
}
