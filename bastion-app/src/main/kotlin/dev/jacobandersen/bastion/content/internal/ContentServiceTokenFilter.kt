package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.content.internal.ContentServiceTokenProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Gates the internal content API on a shared service token presented as a
 * bearer credential (constant-time compare against the configured token),
 * following the Sigil service-token pattern. Requests under `/internal` without
 * a matching token are rejected with 401.
 */
class ContentServiceTokenFilter(
    private val properties: ContentServiceTokenProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val expected = properties.token
        if (expected.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal API is not configured")
            return
        }
        val presented =
            request
                .getHeader(HttpHeaders.AUTHORIZATION)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring("Bearer ".length)
                ?.trim()
        if (presented == null || !constantTimeEquals(expected, presented)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Valid service token required")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
