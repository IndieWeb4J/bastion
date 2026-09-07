package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.micropub.data.domain.Token
import dev.jacobandersen.bastion.micropub.data.entity.TokenEntity
import dev.jacobandersen.bastion.micropub.data.repository.TokenRepository
import dev.jacobandersen.bastion.micropub.security.MicropubToken
import dev.jacobandersen.bastion.micropub.security.MicropubTokenScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class TokenServiceTest {
    private val repository = mock(TokenRepository::class.java)
    private val service = TokenService(repository)

    private val rawToken = "raw-token-value"
    private val decoded = MicropubToken("https://bastion.test", "https://client.example", listOf(MicropubTokenScope.CREATE))

    private fun entity(expiresAt: Instant) =
        TokenEntity(
            token = rawToken,
            decoded = decoded,
            expiresAt = expiresAt,
        ).apply { id = UUID.randomUUID() }

    @Test
    fun `returns the cached token when it is still valid`() {
        val entity = entity(Instant.now().plusSeconds(60))
        `when`(repository.findByToken(rawToken)).thenReturn(entity)

        val result = service.checkToken(rawToken)

        assertNotNull(result)
        assertEquals(entity.id, result!!.id)
        verify(repository, never()).delete(entity)
    }

    @Test
    fun `deletes and returns null for an expired token`() {
        val entity = entity(Instant.now().minusSeconds(60))
        `when`(repository.findByToken(rawToken)).thenReturn(entity)

        val result = service.checkToken(rawToken)

        assertNull(result)
        verify(repository).delete(entity)
    }

    @Test
    fun `returns null for an unknown token`() {
        `when`(repository.findByToken(rawToken)).thenReturn(null)

        assertNull(service.checkToken(rawToken))
    }

    @Test
    fun `rememberToken stores the token with an expiry`() {
        service.rememberToken(rawToken, decoded)

        val captor = ArgumentCaptor.forClass(TokenEntity::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertEquals(rawToken, saved.token)
        assertEquals(decoded, saved.decoded)
        assertEquals(true, saved.expiresAt.isAfter(Instant.now()))
    }

    @Test
    fun `forgetToken deletes the stored token`() {
        val entity = entity(Instant.now().plusSeconds(60))
        `when`(repository.findByToken(rawToken)).thenReturn(entity)

        service.forgetToken(rawToken)

        verify(repository).delete(entity)
    }

    @Test
    fun `forgetToken ignores unknown tokens`() {
        `when`(repository.findByToken(rawToken)).thenReturn(null)

        service.forgetToken(rawToken)

        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any())
    }
}
