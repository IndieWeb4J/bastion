package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.toResponseEntity
import dev.jacobandersen.bastion.micropub.type.resp.writeResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

@Component
class MicropubAuthenticationFilter(
    val validator: MicropubTokenValidator,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        logger.info("Micropub authentication filter: begin")

        val token = try { extractToken(request) } catch (_: Exception) {
            logger.info("Micropub authentication filter: failed to extract token")
            ApiResponse.Error.InvalidRequest(error = "bad_request", errorDescription = "Only one form of access token may be provided").toResponseEntity().writeResponse(response, objectMapper)
            return
        }

        if (token.isBlank()) {
            logger.info("Micropub authentication filter: received blank token")
            ApiResponse.Error.Unauthorized().toResponseEntity().writeResponse(response, objectMapper)
            return
        }

        SecurityContextHolder.getContext().authentication = try {
            logger.info("Micropub authentication filter: begin validating token")
            validator.validateToken(token)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.warn("Micropub authentication filter: failed to validate token")
            ApiResponse.Error.Forbidden().toResponseEntity().writeResponse(response, objectMapper)
            return
        }

        logger.info("Micropub authentication filter: success")
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String {
        val authHeader = request.getHeader("Authorization")
        val bodyToken = request.getParameter("access_token")

        return if (!authHeader.isNullOrBlank() && !bodyToken.isNullOrBlank()) {
            logger.info("Micropub authentication filter: neither auth header or access_token")
            throw IllegalStateException()
        } else if (!authHeader.isNullOrBlank() && authHeader.startsWith("Bearer ")) {
            logger.info("Micropub authentication filter: found Bearer token")
            val parts = authHeader.split(" ")
            if (parts.size != 2) {
                logger.warn("Micropub authentication filter: malformed Bearer token")
                throw IllegalStateException()
            }

            parts[1]
        } else if (!bodyToken.isNullOrBlank()) {
            logger.info("Micropub authentication filter: found body token")
            bodyToken
        } else {
            logger.info("Micropub authentication filter: no access token")
            ""
        }
    }
}