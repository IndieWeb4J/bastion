package dev.jacobandersen.bastion.webmention.data.repository

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.entity.WebmentionNotificationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface WebmentionNotificationRepository : JpaRepository<WebmentionNotificationEntity, UUID> {
    fun findBySourceUrlAndState(
        sourceUrl: String,
        state: WebmentionState,
    ): List<WebmentionNotificationEntity>

    fun findBySourceUrlAndTargetUrl(
        sourceUrl: String,
        targetUrl: String,
    ): WebmentionNotificationEntity?

    fun findByStateInAndDeliveredFalseAndNextAttemptAtNotNullAndNextAttemptAtLessThanEqual(
        states: Collection<WebmentionState>,
        nextAttemptAt: Instant,
    ): List<WebmentionNotificationEntity>
}
