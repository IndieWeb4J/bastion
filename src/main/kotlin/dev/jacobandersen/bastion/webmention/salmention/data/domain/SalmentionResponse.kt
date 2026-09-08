package dev.jacobandersen.bastion.webmention.salmention.data.domain

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import java.time.Instant
import java.util.UUID

/**
 * A nested response discovered on a received webmention's source document: a
 * reply (or other interaction) that another site displayed on the source, which
 * Salmention propagates upstream to the original post.
 */
data class SalmentionResponse(
    val id: UUID,
    val receivedWebmentionId: UUID,
    val sourceUrl: String,
    val responseUrl: String,
    val interaction: WebmentionInteraction,
    val authorName: String?,
    val authorUrl: String?,
    val authorPhoto: String?,
    val contentText: String?,
    val contentHtml: String?,
    val rawMf2: Mf2Object?,
    val firstSeenAt: Instant,
    val updatedAtUtc: Instant,
) {
    /**
     * Whether this record already stores [analysis]'s snapshot, i.e.
     * re-verifying the source would not change what this nested response
     * displays.
     */
    fun matchesAnalysis(analysis: ReceivedWebmentionAnalysis): Boolean =
        interaction == analysis.interaction &&
            authorName == analysis.authorName &&
            authorUrl == analysis.authorUrl &&
            authorPhoto == analysis.authorPhoto &&
            contentText == analysis.contentText &&
            contentHtml == analysis.contentHtml
}
