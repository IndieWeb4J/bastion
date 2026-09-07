package dev.jacobandersen.bastion.webmention.data.service

import dev.jacobandersen.bastion.webmention.data.entity.WebmentionEndpointCacheEntity
import dev.jacobandersen.bastion.webmention.data.repository.WebmentionEndpointCacheRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class WebmentionEndpointCacheServiceTest {

    private val repository = mock(WebmentionEndpointCacheRepository::class.java)
    private val service = WebmentionEndpointCacheService(repository)

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun entity(targetUrl: String, endpointUrl: String?, expiresAt: Instant): WebmentionEndpointCacheEntity {
        return WebmentionEndpointCacheEntity(
            targetUrl = targetUrl,
            endpointUrl = endpointUrl,
            discoveredAt = now,
            expiresAt = expiresAt,
            updatedAt = now,
        )
    }

    @Test
    fun lookupReturnsFreshWithinWindowAndMissAfterExpiry() {
        `when`(repository.findByTargetUrl("a")).thenReturn(entity("a", "https://example.com/wm", now.plusSeconds(60)))

        assertInstanceOf(EndpointCacheResult.Fresh::class.java, service.lookup("a", now))
        assertInstanceOf(EndpointCacheResult.Miss::class.java, service.lookup("a", now.plusSeconds(61)))
    }

    @Test
    fun evictRemovesEntryByTarget() {
        service.evict("a")
        verify(repository).deleteByTargetUrl("a")
    }

    @Test
    fun purgeExpiredDeletesOnlyPastEntries() {
        val expired = listOf(
            entity("gone", null, now),
            entity("gone2", null, now.minusSeconds(1)),
        )
        `when`(repository.findByExpiresAtLessThanEqual(now)).thenReturn(expired)

        assertEquals(2, service.purgeExpired(now))
        verify(repository).deleteAll(expired)
    }
}
