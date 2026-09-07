package dev.jacobandersen.bastion.micropub.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.json.JsonMapper

class MicropubAuthenticationFilterTest {
    private val validator = mock(MicropubTokenValidator::class.java)
    private val mapper = JsonMapper.builderWithJackson2Defaults().build()
    private val filter = MicropubAuthenticationFilter(validator, mapper)

    private lateinit var response: MockHttpServletResponse
    private val chain = mock(FilterChain::class.java)

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        response = MockHttpServletResponse()
        `when`(
            validator.validateToken("test-token"),
        ).thenReturn(
            MicropubAuthentication(
                "test-token",
                MicropubToken("https://bastion.test", "https://client.example", listOf(MicropubTokenScope.CREATE)),
                true,
            ),
        )
    }

    private fun request(
        header: String? = null,
        bodyToken: String? = null,
        method: String = "POST",
        contentType: String? = null,
    ): MockHttpServletRequest {
        val request = MockHttpServletRequest(method, "/micropub")
        header?.let { request.addHeader("Authorization", it) }
        bodyToken?.let { request.addParameter("access_token", it) }
        contentType?.let { request.contentType = it }
        return request
    }

    @Test
    fun `authenticates with a bearer header token`() {
        filter.doFilter(request(header = "Bearer test-token"), response, chain)

        assertEquals(200, response.status)
        assertNotNull(SecurityContextHolder.getContext().authentication)
        assertTrue(SecurityContextHolder.getContext().authentication!!.isAuthenticated)
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `authenticates with a form body token`() {
        filter.doFilter(request(bodyToken = "test-token", contentType = "application/x-www-form-urlencoded"), response, chain)

        assertEquals(200, response.status)
        assertNotNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `authenticates with a lowercase bearer scheme`() {
        filter.doFilter(request(header = "bearer test-token"), response, chain)

        assertEquals(200, response.status)
        assertNotNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `returns 401 when no token is present`() {
        filter.doFilter(request(), response, chain)

        assertEquals(401, response.status)
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"))
        assertTrue(response.contentAsString.contains("unauthorized"))
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `returns 400 when header and body tokens are both present`() {
        filter.doFilter(request(header = "Bearer test-token", bodyToken = "test-token"), response, chain)

        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("invalid_request"))
    }

    @Test
    fun `returns 400 for a malformed bearer header`() {
        filter.doFilter(request(header = "Bearer"), response, chain)

        assertEquals(400, response.status)
    }

    @Test
    fun `returns 401 for a non-bearer scheme`() {
        filter.doFilter(request(header = "Basic dXNlcjpwYXNz"), response, chain)

        assertEquals(401, response.status)
    }

    @Test
    fun `returns 403 when token validation fails`() {
        `when`(validator.validateToken("bad-token")).thenThrow(IllegalArgumentException("nope"))

        filter.doFilter(request(header = "Bearer bad-token"), response, chain)

        assertEquals(403, response.status)
        assertTrue(response.contentAsString.contains("forbidden"))
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `does not leave a stale authentication in the context on failure`() {
        `when`(validator.validateToken("bad-token")).thenThrow(IllegalArgumentException("nope"))

        filter.doFilter(request(header = "Bearer bad-token"), response, chain)

        assertEquals(403, response.status)
        assertEquals(null, SecurityContextHolder.getContext().authentication)
    }
}
