package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.sigil.client.TokenIntrospector
import dev.jacobandersen.sigil.protocol.IntrospectionResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class MicropubTokenValidatorTest {
    private val expectedMe = "https://example.com"

    private val tokenIntrospector = mock(TokenIntrospector::class.java)
    private val validator = MicropubTokenValidator(tokenIntrospector, expectedMe)

    private fun active(
        me: String = expectedMe,
        clientId: String = "https://client.example",
        scope: String = "create",
    ) = IntrospectionResponse(active = true, me = me, clientId = clientId, scope = scope)

    @Test
    fun `resolves a valid token to an authentication with mapped scopes`() {
        `when`(tokenIntrospector.introspect("abc")).thenReturn(active(scope = "create media bogus"))

        val auth = validator.validateToken("abc")

        assertEquals(expectedMe, auth.getName())
        assertEquals(
            listOf(MicropubTokenScope.CREATE, MicropubTokenScope.MEDIA),
            (auth.getDetails() as MicropubToken).scope,
        )
    }

    @Test
    fun `rejects an inactive token`() {
        `when`(tokenIntrospector.introspect("abc")).thenReturn(IntrospectionResponse(active = false))

        assertThrows(IllegalArgumentException::class.java) { validator.validateToken("abc") }
    }

    @Test
    fun `rejects a token for another identity`() {
        `when`(tokenIntrospector.introspect("abc")).thenReturn(active(me = "https://someone.else"))

        assertThrows(IllegalArgumentException::class.java) { validator.validateToken("abc") }
    }

    @Test
    fun `normalization accepts host case trailing slash and fragment differences`() {
        `when`(tokenIntrospector.introspect("abc")).thenReturn(active(me = "https://EXAMPLE.com/#frag"))

        org.junit.jupiter.api.Assertions
            .assertDoesNotThrow { validator.validateToken("abc") }
    }
}
