package dev.jacobandersen.bastion.webmention.data.repository

import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState
import dev.jacobandersen.bastion.webmention.data.entity.ReceivedWebmentionEntity
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReceivedWebmentionRepository : JpaRepository<ReceivedWebmentionEntity, UUID> {
    fun findBySourceUrlAndPostId(sourceUrl: String, postId: UUID): ReceivedWebmentionEntity?

    fun findByPostId(postId: UUID): List<ReceivedWebmentionEntity>

    fun findByPostIdAndState(postId: UUID, state: ReceivedWebmentionState): List<ReceivedWebmentionEntity>

    fun findByPostIdInAndState(
        postIds: Collection<UUID>,
        state: ReceivedWebmentionState,
    ): List<ReceivedWebmentionEntity>
}
