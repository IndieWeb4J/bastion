package dev.jacobandersen.bastion.content.projection

import dev.jacobandersen.beacon.WebmentionInteraction
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * A verified webmention projected from Beacon's `webmention.*` events, so the
 * public read API stays a single local query. Idempotent upsert keyed by
 * (source_url, post_id); no cross-service foreign key.
 */
@Entity
@Table(name = "projected_webmentions")
class ProjectedWebmentionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "post_id", nullable = false)
    var postId: UUID,
    @Column(name = "source_url", nullable = false)
    var sourceUrl: String,
    @Column(name = "target_url", nullable = false)
    var targetUrl: String,
    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    var interaction: WebmentionInteraction? = null,
    @Column(name = "author_name", nullable = true)
    var authorName: String? = null,
    @Column(name = "author_url", nullable = true)
    var authorUrl: String? = null,
    @Column(name = "author_photo", nullable = true)
    var authorPhoto: String? = null,
    @Column(name = "content_text", nullable = true, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    var contentText: List<String>? = null,
    @Column(name = "content_html", nullable = true, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    var contentHtml: List<String>? = null,
    @Column(name = "first_seen_at", nullable = false)
    var firstSeenAt: Instant,
    @Column(name = "verified_at", nullable = true)
    var verifiedAt: Instant? = null,
    @Column(name = "updated_at_utc", nullable = false)
    var updatedAtUtc: Instant,
)
