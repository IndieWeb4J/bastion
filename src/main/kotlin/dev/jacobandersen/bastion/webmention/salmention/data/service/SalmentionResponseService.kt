package dev.jacobandersen.bastion.webmention.salmention.data.service

import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.salmention.data.domain.SalmentionResponse
import dev.jacobandersen.bastion.webmention.salmention.data.entity.SalmentionResponseEntity
import dev.jacobandersen.bastion.webmention.salmention.data.repository.SalmentionResponseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Persistence for Salmention nested responses. Idempotent ingestion is
 * guaranteed by the `(received_webmention_id, response_url)` unique index,
 * mirrored here by a lookup before insert so a repeated identical re-receipt
 * is a no-op. Re-receipts that change a stored response rewrite its snapshot
 * ([refresh]) and responses that are no longer on the source are retired
 * ([retireRemoved]).
 */
@Service
class SalmentionResponseService(
    private val repository: SalmentionResponseRepository,
) {
    @Transactional
    fun ingest(
        sourceUrl: String,
        receivedWebmentionId: UUID,
        responseUrl: String,
        analysis: ReceivedWebmentionAnalysis,
    ): SalmentionResponse? {
        if (repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl) != null) return null

        val now = Instant.now()
        val entity =
            SalmentionResponseEntity(
                receivedWebmentionId = receivedWebmentionId,
                sourceUrl = sourceUrl,
                responseUrl = responseUrl,
                interaction = analysis.interaction,
                authorName = analysis.authorName,
                authorUrl = analysis.authorUrl,
                authorPhoto = analysis.authorPhoto,
                contentText = analysis.contentText,
                contentHtml = analysis.contentHtml,
                rawMf2 = analysis.primary,
                firstSeenAt = now,
                updatedAtUtc = now,
            )
        return repository.save(entity).toDomain()
    }

    /**
     * Rewrites an existing response's snapshot (interaction, author, content and
     * raw microformats) from [analysis] and bumps its updated time. Returns the
     * refreshed response, or null when no row exists for the pair.
     */
    @Transactional
    fun refresh(
        receivedWebmentionId: UUID,
        responseUrl: String,
        analysis: ReceivedWebmentionAnalysis,
    ): SalmentionResponse? {
        val entity =
            repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)
                ?: return null
        entity.interaction = analysis.interaction
        entity.authorName = analysis.authorName
        entity.authorUrl = analysis.authorUrl
        entity.authorPhoto = analysis.authorPhoto
        entity.contentText = analysis.contentText
        entity.contentHtml = analysis.contentHtml
        entity.rawMf2 = analysis.primary
        entity.updatedAtUtc = Instant.now()
        return repository.save(entity).toDomain()
    }

    /**
     * Deletes the stored responses of a received webmention whose URL is no
     * longer among [keepResponseUrls], returning how many were removed. A
     * response that is absent from the freshly re-fetched source is gone from
     * the thread and must no longer surface.
     */
    @Transactional
    fun retireRemoved(
        receivedWebmentionId: UUID,
        keepResponseUrls: Set<String>,
    ): Int {
        val toRemove =
            repository
                .findByReceivedWebmentionId(receivedWebmentionId)
                .filter { it.responseUrl !in keepResponseUrls }
        if (toRemove.isEmpty()) return 0
        repository.deleteAll(toRemove)
        return toRemove.size
    }

    @Transactional(readOnly = true)
    fun byReceivedWebmention(receivedWebmentionId: UUID): List<SalmentionResponse> =
        repository.findByReceivedWebmentionId(receivedWebmentionId).map { it.toDomain() }

    @Transactional
    fun retireByReceivedWebmention(receivedWebmentionId: UUID): Int = repository.deleteByReceivedWebmentionId(receivedWebmentionId)
}
