package dev.jacobandersen.bastion.webmention.data.service

import dev.jacobandersen.bastion.webmention.data.entity.WebmentionEndpointCacheEntity
import dev.jacobandersen.bastion.webmention.data.repository.WebmentionEndpointCacheRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

sealed interface EndpointCacheResult {
    data class Fresh(
        val endpointUrl: String?,
    ) : EndpointCacheResult

    data object Miss : EndpointCacheResult
}

@Service
class WebmentionEndpointCacheService(
    private val repository: WebmentionEndpointCacheRepository,
) {
    @Transactional(readOnly = true)
    fun lookup(
        targetUrl: String,
        now: Instant,
    ): EndpointCacheResult {
        val entry = repository.findByTargetUrl(targetUrl) ?: return EndpointCacheResult.Miss
        return if (entry.expiresAt.isAfter(now)) {
            EndpointCacheResult.Fresh(entry.endpointUrl)
        } else {
            EndpointCacheResult.Miss
        }
    }

    @Transactional
    fun store(
        targetUrl: String,
        endpointUrl: String?,
        expiresAt: Instant,
    ) {
        val now = Instant.now()
        val entry =
            repository.findByTargetUrl(targetUrl)
                ?: WebmentionEndpointCacheEntity(
                    targetUrl = targetUrl,
                    discoveredAt = now,
                    expiresAt = expiresAt,
                    updatedAt = now,
                )
        entry.endpointUrl = endpointUrl
        entry.discoveredAt = now
        entry.expiresAt = expiresAt
        entry.updatedAt = now
        repository.save(entry)
    }

    /** Remove any cached entry for the target, e.g. after an uncacheable discovery. */
    @Transactional
    fun evict(targetUrl: String) {
        repository.deleteByTargetUrl(targetUrl)
    }

    /** Delete entries whose cache window has passed, returning how many were removed. */
    @Transactional
    fun purgeExpired(now: Instant): Int {
        val expired = repository.findByExpiresAtLessThanEqual(now)
        if (expired.isNotEmpty()) {
            repository.deleteAll(expired)
        }
        return expired.size
    }
}
