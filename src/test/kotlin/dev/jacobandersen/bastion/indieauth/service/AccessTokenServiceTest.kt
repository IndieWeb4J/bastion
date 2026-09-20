package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.entity.AuthorizationCodeEntity
import dev.jacobandersen.bastion.indieauth.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import dev.jacobandersen.bastion.indieauth.security.Pkce
import dev.jacobandersen.bastion.indieauth.security.Tokens
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.time.Instant

class AccessTokenServiceTest {
    private val config = IndieAuthConfig(me = "https://bastion.test")
    private val accessTokenRepository = mock(AccessTokenRepository::class.java)
    private val authorizationCodeRepository = mock(AuthorizationCodeRepository::class.java)
    private val service = AccessTokenService(config, accessTokenRepository, authorizationCodeRepository)

    private val rawCode = "raw-authorization-code"
    private val codeHash = Tokens.sha256(rawCode)
    private val clientId = "https://client.example"
    private val redirectUri = "https://client.example/callback"

    private fun code(
        clientId: String = this.clientId,
        redirectUri: String = this.redirectUri,
        expiresAt: Instant = Instant.now().plusSeconds(60),
        codeChallenge: String? = null,
    ) = AuthorizationCodeEntity(
        codeHash = codeHash,
        clientId = clientId,
        redirectUri = redirectUri,
        me = "https://bastion.test",
        scope = "create",
        codeChallenge = codeChallenge,
        expiresAt = expiresAt,
        createdAt = Instant.now(),
    )

    @Test
    fun `exchange issues a token for a valid code`() {
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(code())
        `when`(authorizationCodeRepository.claim(eq(codeHash), any())).thenReturn(1)

        val issued = service.exchange(rawCode, clientId, redirectUri, null)

        assertEquals("https://bastion.test", issued.me)
        assertEquals("create", issued.scope)
        assertTrue(issued.accessToken.isNotBlank())
        val captor =
            ArgumentCaptor.forClass(dev.jacobandersen.bastion.indieauth.data.entity.AccessTokenEntity::class.java)
        verify(accessTokenRepository).save(captor.capture())
        assertEquals(Tokens.sha256(issued.accessToken), captor.value.tokenHash)
    }

    @Test
    fun `exchange rejects an unknown code`() {
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(null)

        assertInvalidGrant { service.exchange(rawCode, clientId, redirectUri, null) }
    }

    @Test
    fun `exchange rejects an expired code`() {
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(
            code(
                expiresAt = Instant.now().minusSeconds(1),
            ),
        )

        assertInvalidGrant { service.exchange(rawCode, clientId, redirectUri, null) }
    }

    @Test
    fun `exchange rejects a mismatched client id`() {
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(code())

        assertInvalidGrant { service.exchange(rawCode, "https://evil.example", redirectUri, null) }
    }

    @Test
    fun `exchange rejects a mismatched redirect uri`() {
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(code())

        assertInvalidGrant { service.exchange(rawCode, clientId, "https://evil.example/callback", null) }
    }

    @Test
    fun `exchange enforces pkce when a challenge is present`() {
        val verifier = "a-verifier"
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(code(codeChallenge = Pkce.s256(verifier)))

        assertInvalidGrant { service.exchange(rawCode, clientId, redirectUri, "wrong-verifier") }
    }

    @Test
    fun `exchange accepts a matching pkce verifier`() {
        val verifier = "a-verifier"
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(code(codeChallenge = Pkce.s256(verifier)))
        `when`(authorizationCodeRepository.claim(eq(codeHash), any())).thenReturn(1)

        val issued = service.exchange(rawCode, clientId, redirectUri, verifier)

        assertEquals("https://bastion.test", issued.me)
    }

    @Test
    fun `exchange rejects an already used code`() {
        `when`(authorizationCodeRepository.findByCodeHash(codeHash)).thenReturn(code())
        `when`(authorizationCodeRepository.claim(eq(codeHash), any())).thenReturn(0)

        assertInvalidGrant { service.exchange(rawCode, clientId, redirectUri, null) }
    }

    @Test
    fun `resolve returns null for an unknown token`() {
        `when`(accessTokenRepository.findByTokenHash(org.mockito.ArgumentMatchers.anyString())).thenReturn(null)

        assertNull(service.resolve("raw-token"))
    }

    private fun assertInvalidGrant(block: () -> Unit) {
        val e = assertThrows(IndieAuthException::class.java) { block() }
        assertEquals(IndieAuthError.Code.INVALID_GRANT, e.code)
    }
}
