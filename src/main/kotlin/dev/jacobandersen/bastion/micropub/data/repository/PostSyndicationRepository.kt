package dev.jacobandersen.bastion.micropub.data.repository

import dev.jacobandersen.bastion.micropub.data.entity.PostSyndicationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PostSyndicationRepository : JpaRepository<PostSyndicationEntity, UUID> {
    fun findByPostId(postId: UUID): List<PostSyndicationEntity>

    fun findByPostIdAndTargetUid(
        postId: UUID,
        targetUid: String,
    ): PostSyndicationEntity?
}
