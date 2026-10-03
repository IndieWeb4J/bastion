package dev.jacobandersen.bastion.indieauth.type

import org.springframework.http.HttpStatus
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * An OAuth 2.0 / IndieAuth protocol error, serialized as
 * `{"error": "...", "error_description": "..."}`. The [Code] enum covers the
 * errors the authorization and token endpoints must produce.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class IndieAuthError(
    val error: String,
    val errorDescription: String? = null,
) {
    enum class Code(
        val value: String,
        val status: HttpStatus,
    ) {
        INVALID_REQUEST("invalid_request", HttpStatus.BAD_REQUEST),
        INVALID_CLIENT("invalid_client", HttpStatus.UNAUTHORIZED),
        INVALID_TOKEN("invalid_token", HttpStatus.UNAUTHORIZED),
        INVALID_GRANT("invalid_grant", HttpStatus.BAD_REQUEST),
        INVALID_SCOPE("invalid_scope", HttpStatus.BAD_REQUEST),
        INSUFFICIENT_SCOPE("insufficient_scope", HttpStatus.FORBIDDEN),
        UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type", HttpStatus.BAD_REQUEST),
        UNSUPPORTED_GRANT_TYPE("unsupported_grant_type", HttpStatus.BAD_REQUEST),
        ACCESS_DENIED("access_denied", HttpStatus.FORBIDDEN),
        SERVER_ERROR("server_error", HttpStatus.INTERNAL_SERVER_ERROR),
    }

    companion object {
        fun of(
            code: Code,
            description: String? = null,
        ) = IndieAuthError(code.value, description)
    }
}
