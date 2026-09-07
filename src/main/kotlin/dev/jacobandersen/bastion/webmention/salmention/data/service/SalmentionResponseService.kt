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
 * is a no-op.
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

    @Transactional(readOnly = true)
    fun responseUrlsByReceivedWebmention(receivedWebmentionId: UUID): Set<String> = repository.findByReceivedWebmentionId(receivedWebmentionId).map { it.responseUrl }.toSet()

    @Transactional(readOnly = true)
    fun byReceivedWebmention(receivedWebmentionId: UUID): List<SalmentionResponse> = repository.findByReceivedWebmentionId(receivedWebmentionId).map { it.toDomain() }

    @Transactional
    fun retireByReceivedWebmention(receivedWebmentionId: UUID): Int = repository.deleteByReceivedWebmentionId(receivedWebmentionId)
}
