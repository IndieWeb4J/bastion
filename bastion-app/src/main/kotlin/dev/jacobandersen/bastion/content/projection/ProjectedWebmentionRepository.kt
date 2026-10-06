package dev.jacobandersen.bastion.content.projection

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProjectedWebmentionRepository : JpaRepository<ProjectedWebmentionEntity, UUID> {
    fun findBySourceUrlAndPostId(
        sourceUrl: String,
        postId: UUID,
    ): ProjectedWebmentionEntity?

    fun findByPostIdOrderByFirstSeenAtAsc(postId: UUID): List<ProjectedWebmentionEntity>

    fun findByPostIdIn(postIds: Collection<UUID>): List<ProjectedWebmentionEntity>

    fun deleteBySourceUrlAndPostId(
        sourceUrl: String,
        postId: UUID,
    ): Long
}
