package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.indieauth.data.domain.IssuedAccessToken
import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class MicropubTokenValidatorTest {
    private val expectedMe = "https://example.com"

    private val accessTokenService = mock(AccessTokenService::class.java)
    private val validator = MicropubTokenValidator(accessTokenService, expectedMe)

    private fun issued(
        me: String = expectedMe,
        clientId: String = "https://client.example",
        scope: List<String> = listOf("create"),
    ) = IssuedAccessToken(me, clientId, scope, Instant.now().plusSeconds(60))

    @Test
    fun `resolves a valid token to an authentication with mapped scopes`() {
        `when`(accessTokenService.resolve("abc")).thenReturn(issued(scope = listOf("create", "media", "bogus")))

        val auth = validator.validateToken("abc")

        assertEquals(expectedMe, auth.getName())
        assertEquals(
            listOf(MicropubTokenScope.CREATE, MicropubTokenScope.MEDIA),
            (auth.getDetails() as MicropubToken).scope,
        )
    }

    @Test
    fun `rejects an unknown token`() {
        `when`(accessTokenService.resolve("abc")).thenReturn(null)

        assertThrows(IllegalArgumentException::class.java) { validator.validateToken("abc") }
    }

    @Test
    fun `rejects a token for another identity`() {
        `when`(accessTokenService.resolve("abc")).thenReturn(issued(me = "https://someone.else"))

        assertThrows(IllegalArgumentException::class.java) { validator.validateToken("abc") }
    }

    @Test
    fun `normalization accepts host case trailing slash and fragment differences`() {
        `when`(accessTokenService.resolve("abc")).thenReturn(issued(me = "https://EXAMPLE.com/#frag"))

        org.junit.jupiter.api.Assertions
            .assertDoesNotThrow { validator.validateToken("abc") }
    }
}
