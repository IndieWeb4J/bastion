package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.micropub.data.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.micropub.data.repository.PostSyndicationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PostSyndicationService(
    private val repository: PostSyndicationRepository,
) {
    @Transactional
    fun record(
        postId: UUID,
        targetUid: String,
    ): PostSyndicationEntity = repository.save(PostSyndicationEntity(postId = postId, targetUid = targetUid))

    @Transactional
    fun recordOutcome(
        postId: UUID,
        targetUid: String,
        syndicatedUrl: String,
    ) {
        val entity = repository.findByPostIdAndTargetUid(postId, targetUid) ?: return
        entity.syndicatedUrl = syndicatedUrl
        repository.save(entity)
    }

    @Transactional(readOnly = true)
    fun findByPostId(postId: UUID): List<PostSyndicationEntity> = repository.findByPostId(postId)

    @Transactional
    fun remove(
        postId: UUID,
        targetUid: String,
    ) {
        repository.deleteByPostIdAndTargetUid(postId, targetUid)
    }
}
