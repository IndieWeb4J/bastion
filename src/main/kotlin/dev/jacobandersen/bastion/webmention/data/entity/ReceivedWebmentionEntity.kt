package dev.jacobandersen.bastion.webmention.data.entity

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmention
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.*

@Entity
@Table(name = "received_webmentions")
class ReceivedWebmentionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var postId: UUID,
    @Column(nullable = false)
    var sourceUrl: String,
    @Column(nullable = false)
    var targetUrl: String,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    var state: ReceivedWebmentionState,
    @Column(nullable = true)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    var interaction: WebmentionInteraction?,
    @Column(nullable = true)
    var authorName: String?,
    @Column(nullable = true)
    var authorUrl: String?,
    @Column(nullable = true)
    var authorPhoto: String?,
    @Column(nullable = true)
    var contentText: String?,
    @Column(nullable = true)
    var contentHtml: String?,
    @Column(nullable = true)
    @JdbcTypeCode(SqlTypes.JSON)
    var rawMf2: Mf2Object?,
    @Column(nullable = true)
    var lastError: String?,
    @Column(nullable = false)
    var firstSeenAt: Instant,
    @Column(nullable = true)
    var verifiedAt: Instant?,
    @Column(nullable = false)
    var updatedAtUtc: Instant,
) {
    fun toDomain(): ReceivedWebmention =
        ReceivedWebmention(
            id = requireNotNull(id),
            postId = postId,
            sourceUrl = sourceUrl,
            targetUrl = targetUrl,
            state = state,
            interaction = interaction,
            authorName = authorName,
            authorUrl = authorUrl,
            authorPhoto = authorPhoto,
            contentText = contentText,
            contentHtml = contentHtml,
            rawMf2 = rawMf2,
            lastError = lastError,
            firstSeenAt = firstSeenAt,
            verifiedAt = verifiedAt,
            updatedAtUtc = updatedAtUtc,
        )
}
