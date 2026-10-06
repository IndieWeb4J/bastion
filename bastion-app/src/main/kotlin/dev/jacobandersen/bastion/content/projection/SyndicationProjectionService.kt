package dev.jacobandersen.bastion.content.projection

import dev.jacobandersen.conduit.event.SyndicationEvent
import dev.jacobandersen.conduit.event.SyndicationEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Applies Conduit's `syndication.*` events to Bastion's read-model projection.
 * Idempotent: syndicated upserts by (post_id, target_uid); retracted deletes.
 */
@Service
class SyndicationProjectionService(
    private val repository: ProjectedSyndicationRepository,
) {
    @Transactional
    fun apply(event: SyndicationEvent) {
        val postId = runCatching { UUID.fromString(event.postId) }.getOrNull() ?: return
        when (event.eventType) {
            SyndicationEventType.SYNDICATED -> {
                val url = event.syndicatedUrl ?: return
                val now = Instant.now()
                val entity =
                    repository.findByPostIdAndTargetUid(postId, event.targetUid)
                        ?: ProjectedSyndicationEntity(
                            postId = postId,
                            targetUid = event.targetUid,
                            url = url,
                            updatedAtUtc = now,
                        )
                entity.name = event.targetName
                entity.url = url
                entity.updatedAtUtc = now
                repository.save(entity)
            }

            SyndicationEventType.RETRACTED -> {
                repository.deleteByPostIdAndTargetUid(postId, event.targetUid)
            }
        }
    }

    @Transactional(readOnly = true)
    fun byPost(postId: UUID): List<ProjectedSyndicationEntity> = repository.findByPostId(postId)
}
