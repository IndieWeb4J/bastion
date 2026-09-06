package dev.jacobandersen.bastion.webmention.data.service

import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmention
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState.DELETED
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState.ERROR
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState.PENDING
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState.REJECTED
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState.VERIFIED
import dev.jacobandersen.bastion.webmention.data.entity.ReceivedWebmentionEntity
import dev.jacobandersen.bastion.webmention.data.repository.ReceivedWebmentionRepository
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReceivedWebmentionService(
    private val repository: ReceivedWebmentionRepository,
) {
    /**
     * Ensure a received webmention exists in [PENDING] state for the given
     * source and post, so verification can be (re)run against it. A webmention
     * that was previously deleted, rejected or errored is reopened.
     */
    @Transactional
    fun ensurePending(sourceUrl: String, targetUrl: String, postId: UUID): ReceivedWebmention {
        val now = Instant.now()
        val existing = repository.findBySourceUrlAndPostId(sourceUrl, postId)
        if (existing == null) {
            val created = ReceivedWebmentionEntity(
                postId = postId,
                sourceUrl = sourceUrl,
                targetUrl = targetUrl,
                state = PENDING,
                interaction = null,
                authorName = null,
                authorUrl = null,
                authorPhoto = null,
                contentText = null,
                contentHtml = null,
                rawMf2 = null,
                lastError = null,
                firstSeenAt = now,
                verifiedAt = null,
                updatedAtUtc = now,
            )
            return repository.save(created).toDomain()
        }

        if (existing.state != PENDING) {
            existing.state = PENDING
            existing.lastError = null
            existing.verifiedAt = null
            existing.updatedAtUtc = now
            return repository.save(existing).toDomain()
        }
        return existing.toDomain()
    }

    @Transactional
    fun markVerified(sourceUrl: String, postId: UUID, analysis: ReceivedWebmentionAnalysis): ReceivedWebmention {
        val now = Instant.now()
        val entity = repository.findBySourceUrlAndPostId(sourceUrl, postId)
            ?: throw IllegalStateException("No received webmention for $sourceUrl on post $postId")
        entity.state = VERIFIED
        entity.interaction = analysis.interaction
        entity.authorName = analysis.authorName
        entity.authorUrl = analysis.authorUrl
        entity.authorPhoto = analysis.authorPhoto
        entity.contentText = analysis.contentText
        entity.contentHtml = analysis.contentHtml
        entity.rawMf2 = analysis.primary
        entity.lastError = null
        entity.verifiedAt = now
        entity.updatedAtUtc = now
        return repository.save(entity).toDomain()
    }

    @Transactional
    fun markRejected(sourceUrl: String, postId: UUID, reason: String): ReceivedWebmention {
        return setTerminal(sourceUrl, postId, REJECTED, reason)
    }

    @Transactional
    fun markDeleted(sourceUrl: String, postId: UUID): ReceivedWebmention {
        return setTerminal(sourceUrl, postId, DELETED, null)
    }

    @Transactional
    fun markError(sourceUrl: String, postId: UUID, reason: String): ReceivedWebmention {
        return setTerminal(sourceUrl, postId, ERROR, reason)
    }

    @Transactional(readOnly = true)
    fun notification(sourceUrl: String, postId: UUID): ReceivedWebmention? {
        return repository.findBySourceUrlAndPostId(sourceUrl, postId)?.toDomain()
    }

    @Transactional(readOnly = true)
    fun byPost(postId: UUID): List<ReceivedWebmention> {
        return repository.findByPostId(postId).map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    fun verifiedByPost(postId: UUID): List<ReceivedWebmention> {
        return repository.findByPostIdAndState(postId, VERIFIED).map { it.toDomain() }
    }

    private fun setTerminal(
        sourceUrl: String,
        postId: UUID,
        state: ReceivedWebmentionState,
        reason: String?,
    ): ReceivedWebmention {
        val now = Instant.now()
        val entity = repository.findBySourceUrlAndPostId(sourceUrl, postId)
            ?: throw IllegalStateException("No received webmention for $sourceUrl on post $postId")

        entity.state = state
        entity.lastError = reason
        entity.verifiedAt = null
        if (state == DELETED) {
            entity.interaction = null
            entity.authorName = null
            entity.authorUrl = null
            entity.authorPhoto = null
            entity.contentText = null
            entity.contentHtml = null
            entity.rawMf2 = null
        }
        entity.updatedAtUtc = now
        return repository.save(entity).toDomain()
    }
}
