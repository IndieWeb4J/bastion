package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthRequestRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant

class IndieAuthRowPurgeServiceTest {
    private val config = IndieAuthConfig(me = "https://bastion.test")
    private val authRequestRepository = mock(AuthRequestRepository::class.java)
    private val authorizationCodeRepository = mock(AuthorizationCodeRepository::class.java)
    private val accessTokenRepository = mock(AccessTokenRepository::class.java)
    private val service = IndieAuthRowPurgeService(config, authRequestRepository, authorizationCodeRepository, accessTokenRepository)

    @Test
    fun `purge deletes expired requests and tokens and codes past retention`() {
        val now = Instant.parse("2026-01-02T03:04:05Z")
        `when`(authRequestRepository.deleteExpired(now)).thenReturn(2)
        `when`(authorizationCodeRepository.deleteDead(now.minus(config.codeRetention))).thenReturn(3)
        `when`(accessTokenRepository.deleteExpired(now)).thenReturn(4)

        val purged = service.purge(now)

        assertEquals(9, purged)
        verify(authRequestRepository).deleteExpired(now)
        verify(authorizationCodeRepository).deleteDead(now.minus(config.codeRetention))
        verify(accessTokenRepository).deleteExpired(now)
    }
}
