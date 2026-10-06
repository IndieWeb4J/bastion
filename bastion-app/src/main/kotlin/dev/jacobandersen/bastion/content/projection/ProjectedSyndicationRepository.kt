package dev.jacobandersen.bastion.content.projection

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectedSyndicationRepository : JpaRepository<ProjectedSyndicationEntity, UUID> {
    fun findByPostId(postId: UUID): List<ProjectedSyndicationEntity>

    fun findByPostIdAndTargetUid(
        postId: UUID,
        targetUid: String,
    ): ProjectedSyndicationEntity?

    fun deleteByPostIdAndTargetUid(
        postId: UUID,
        targetUid: String,
    ): Long
}
