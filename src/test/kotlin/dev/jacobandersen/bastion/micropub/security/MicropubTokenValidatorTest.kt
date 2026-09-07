package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.data.domain.Token
import dev.jacobandersen.bastion.micropub.data.service.TokenService
import dev.jacobandersen.bastion.micropub.security.auth.IndieAuthService
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MicropubTokenValidatorTest {

    private val expectedMe = "https://example.com"

    private val tokenService = mock(TokenService::class.java)
    private val indieAuth = mock(IndieAuthService::class.java)
    private val validator = MicropubTokenValidator(tokenService, indieAuth, expectedMe)

    private fun token(me: String = expectedMe, clientId: String = "https://client.example") = MicropubToken(
        me = me,
        clientId = clientId,
        scope = listOf(MicropubTokenScope.CREATE),
    )

    private fun cached(rawToken: String, me: String = expectedMe): Token {
        return Token(
            id = UUID.randomUUID(),
            token = rawToken,
            decoded = token(me),
            expiresAt = Instant.now().plusSeconds(60),
        )
    }

    @Test
    fun matchingCachedTokenIsUsedWithoutCallingIndieAuth() {
        `when`(tokenService.checkToken("abc")).thenReturn(cached("abc"))

        val auth = validator.validateToken("abc")

        assertEquals(expectedMe, auth.getName())
        verify(indieAuth, never()).modernValidation("abc")
    }

    @Test
    fun mismatchedCachedTokenIsEvictedAndRevalidated() {
        `when`(tokenService.checkToken("abc")).thenReturn(cached("abc", me = "https://someone.else"))
        `when`(indieAuth.modernValidation("abc")).thenReturn(token())

        val auth = validator.validateToken("abc")

        assertEquals(expectedMe, auth.getName())
        verify(tokenService).forgetToken("abc")
        verify(tokenService).rememberToken("abc", token())
    }

    @Test
    fun freshTokenForAnotherIdentityIsRejectedAndNotRemembered() {
        `when`(tokenService.checkToken("abc")).thenReturn(null)
        `when`(indieAuth.modernValidation("abc")).thenReturn(token(me = "https://someone.else"))

        assertThrows(IllegalArgumentException::class.java) { validator.validateToken("abc") }
        verify(tokenService, never()).rememberToken("abc", token())
    }

    @Test
    fun normalizationAcceptsHostCaseTrailingSlashAndFragmentDifferences() {
        `when`(tokenService.checkToken("abc")).thenReturn(null)
        `when`(indieAuth.modernValidation("abc")).thenReturn(token(me = "https://EXAMPLE.com/#frag"))

        org.junit.jupiter.api.Assertions.assertDoesNotThrow { validator.validateToken("abc") }
        verify(tokenService).rememberToken("abc", token(me = "https://EXAMPLE.com/#frag"))
    }
}
