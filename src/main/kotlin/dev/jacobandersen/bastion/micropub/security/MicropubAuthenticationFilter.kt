package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.toResponseEntity
import dev.jacobandersen.bastion.micropub.type.resp.writeResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

private val log = KotlinLogging.logger {}

/**
 * Authenticates Micropub requests by extracting a bearer token from the
 * Authorization header or the `access_token` form/query parameter and
 * validating it against the token endpoint. Per the Micropub living standard
 * a missing token is a 401 and an invalid one a 403; scope violations surface
 * later as 403 `insufficient_scope`.
 */
class MicropubAuthenticationFilter(
    private val validator: MicropubTokenValidator,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        when (val extracted = extractToken(request)) {
            is ExtractedToken.Invalid -> {
                respond(
                    response,
                    ApiResponse.Error.InvalidRequest(errorDescription = "Only one form of access token may be provided"),
                )
            }

            ExtractedToken.Missing -> {
                respond(response, ApiResponse.Error.Unauthorized())
            }

            is ExtractedToken.Valid -> {
                SecurityContextHolder.getContext().authentication =
                    try {
                        validator.validateToken(extracted.value)
                    } catch (e: Exception) {
                        log.warn(e) { "Micropub token validation failed" }
                        respond(response, ApiResponse.Error.Forbidden())
                        return
                    }
                filterChain.doFilter(request, response)
            }
        }
    }

    private fun extractToken(request: HttpServletRequest): ExtractedToken {
        val authHeader = request.getHeader("Authorization")
        val bodyToken = request.getParameter("access_token")

        if (authHeader.isNullOrBlank()) {
            return if (bodyToken.isNullOrBlank()) ExtractedToken.Missing else ExtractedToken.Valid(bodyToken)
        }
        if (!bodyToken.isNullOrBlank()) {
            return ExtractedToken.Invalid
        }

        val parts = authHeader.split(" ")
        if (!parts[0].equals("Bearer", ignoreCase = true)) {
            // Another scheme cannot carry a Micropub bearer token.
            return ExtractedToken.Missing
        }
        return if (parts.size == 2 && parts[1].isNotBlank()) {
            ExtractedToken.Valid(parts[1])
        } else {
            ExtractedToken.Invalid
        }
    }

    private fun respond(
        response: HttpServletResponse,
        apiResponse: ApiResponse<*>,
    ) {
        apiResponse.toResponseEntity().writeResponse(response, objectMapper)
    }

    private sealed interface ExtractedToken {
        data class Valid(
            val value: String,
        ) : ExtractedToken

        data object Missing : ExtractedToken

        data object Invalid : ExtractedToken
    }
}
