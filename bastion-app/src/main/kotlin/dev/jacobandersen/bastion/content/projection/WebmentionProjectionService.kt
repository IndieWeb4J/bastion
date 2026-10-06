package dev.jacobandersen.bastion.content.projection

import dev.jacobandersen.beacon.event.WebmentionEvent
import dev.jacobandersen.beacon.event.WebmentionEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Applies Beacon's `webmention.*` events to Bastion's read-model projection.
 * Idempotent: verified/changed upsert by (source_url, post_id); removed deletes.
 */
@Service
class WebmentionProjectionService(
    private val repository: ProjectedWebmentionRepository,
) {
    @Transactional
    fun apply(event: WebmentionEvent) {
        val postId = runCatching { UUID.fromString(event.postId) }.getOrNull() ?: return
        when (event.eventType) {
            WebmentionEventType.VERIFIED, WebmentionEventType.CHANGED -> upsert(event, postId)
            WebmentionEventType.REMOVED -> repository.deleteBySourceUrlAndPostId(event.sourceUrl, postId)
        }
    }

    @Transactional(readOnly = true)
    fun byPost(postId: UUID): List<ProjectedWebmentionEntity> = repository.findByPostIdOrderByFirstSeenAtAsc(postId)

    @Transactional(readOnly = true)
    fun byPosts(postIds: Collection<UUID>): List<ProjectedWebmentionEntity> =
        if (postIds.isEmpty()) emptyList() else repository.findByPostIdIn(postIds)

    private fun upsert(
        event: WebmentionEvent,
        postId: UUID,
    ) {
        val now = Instant.now()
        val entity =
            repository.findBySourceUrlAndPostId(event.sourceUrl, postId)
                ?: ProjectedWebmentionEntity(
                    postId = postId,
                    sourceUrl = event.sourceUrl,
                    targetUrl = event.targetUrl,
                    firstSeenAt = now,
                    updatedAtUtc = now,
                )
        entity.targetUrl = event.targetUrl
        entity.interaction = event.interaction
        entity.authorName = event.authorName
        entity.authorUrl = event.authorUrl
        entity.authorPhoto = event.authorPhoto
        entity.contentText = event.contentText
        entity.contentHtml = event.contentHtml
        entity.verifiedAt = event.verifiedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: now
        entity.updatedAtUtc = now
        repository.save(entity)
    }
}
