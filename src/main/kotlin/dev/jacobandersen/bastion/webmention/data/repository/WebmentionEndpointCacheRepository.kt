package dev.jacobandersen.bastion.webmention.data.repository

import dev.jacobandersen.bastion.webmention.data.entity.WebmentionEndpointCacheEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface WebmentionEndpointCacheRepository : JpaRepository<WebmentionEndpointCacheEntity, String> {
    fun findByTargetUrl(targetUrl: String): WebmentionEndpointCacheEntity?

    fun deleteByTargetUrl(targetUrl: String)

    fun findByExpiresAtLessThanEqual(expiresAt: Instant): List<WebmentionEndpointCacheEntity>
}
