package dev.jacobandersen.bastion.webmention.data.domain

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.webmention.data.entity.ReceivedWebmentionEntity
import java.time.Instant
import java.util.*

data class ReceivedWebmention(
    val id: UUID,
    val postId: UUID,
    val sourceUrl: String,
    val targetUrl: String,
    val state: ReceivedWebmentionState,
    val interaction: WebmentionInteraction?,
    val authorName: String?,
    val authorUrl: String?,
    val authorPhoto: String?,
    val contentText: String?,
    val contentHtml: String?,
    val rawMf2: Mf2Object?,
    val lastError: String?,
    val firstSeenAt: Instant,
    val verifiedAt: Instant?,
    val updatedAtUtc: Instant,
) {
    fun toEntity(): ReceivedWebmentionEntity =
        ReceivedWebmentionEntity(
            id = id,
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
