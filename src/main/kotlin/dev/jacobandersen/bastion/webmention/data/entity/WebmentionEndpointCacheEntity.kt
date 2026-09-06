package dev.jacobandersen.bastion.webmention.data.entity

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionEndpointCache
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "webmention_endpoint_cache")
class WebmentionEndpointCacheEntity(
    @Id
    @Column(nullable = false)
    var targetUrl: String,

    @Column(nullable = true)
    var endpointUrl: String? = null,

    @Column(nullable = false)
    var discoveredAt: Instant,

    @Column(nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var updatedAt: Instant,
) {
    fun toDomain(): WebmentionEndpointCache {
        return WebmentionEndpointCache(
            targetUrl = this.targetUrl,
            endpointUrl = this.endpointUrl,
            discoveredAt = this.discoveredAt,
            expiresAt = this.expiresAt,
        )
    }
}
