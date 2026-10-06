package dev.jacobandersen.bastion.webmention.data.service

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionNotification
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.entity.WebmentionNotificationEntity
import dev.jacobandersen.bastion.webmention.data.repository.WebmentionNotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class WebmentionNotificationService(
    private val repository: WebmentionNotificationRepository,
) {
    @Transactional
    fun setActivePending(
        sourceUrl: String,
        targetUrl: String,
    ) {
        transitionToPending(sourceUrl, targetUrl, WebmentionState.ACTIVE)
    }

    @Transactional
    fun markInactivePendingRetraction(
        sourceUrl: String,
        targetUrl: String,
    ) {
        transitionToPending(sourceUrl, targetUrl, WebmentionState.INACTIVE)
    }

    @Transactional
    fun markInactiveSilent(
        sourceUrl: String,
        targetUrl: String,
    ) {
        val entity = repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl) ?: return
        setSilentInactive(entity, Instant.now())
        repository.save(entity)
    }

    @Transactional
    fun inactivateAllBySource(sourceUrl: String) {
        val now = Instant.now()
        repository.findBySourceUrlAndState(sourceUrl, WebmentionState.ACTIVE).forEach { entity ->
            setSilentInactive(entity, now)
        }
    }

    @Transactional
    fun recordSuccess(
        sourceUrl: String,
        targetUrl: String,
        statusCode: Int?,
    ) {
        applyOutcome(sourceUrl, targetUrl) { entity, now ->
            entity.delivered = true
            entity.attempts = 0
            entity.lastError = null
            entity.lastStatusCode = statusCode
            entity.lastAttemptAt = now
            entity.nextAttemptAt = null
        }
    }

    @Transactional
    fun recordFailure(
        sourceUrl: String,
        targetUrl: String,
        statusCode: Int?,
        error: String,
    ): Int {
        var attempts = 0
        applyOutcome(sourceUrl, targetUrl) { entity, now ->
            entity.delivered = false
            entity.attempts += 1
            attempts = entity.attempts
            entity.lastError = error
            entity.lastStatusCode = statusCode
            entity.lastAttemptAt = now
        }
        return attempts
    }

    @Transactional
    fun scheduleNextAttempt(
        sourceUrl: String,
        targetUrl: String,
        nextAttemptAt: Instant?,
    ) {
        val entity = repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl) ?: return
        entity.nextAttemptAt = nextAttemptAt
        entity.updatedAtUtc = Instant.now()
        repository.save(entity)
    }

    @Transactional(readOnly = true)
    fun notification(
        sourceUrl: String,
        targetUrl: String,
    ): WebmentionNotification? = repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)?.toDomain()

    @Transactional(readOnly = true)
    fun activeNotificationsBySource(sourceUrl: String): List<WebmentionNotification> =
        repository.findBySourceUrlAndState(sourceUrl, WebmentionState.ACTIVE).map { it.toDomain() }

    @Transactional(readOnly = true)
    fun dueForRetry(
        now: Instant,
        states: Collection<WebmentionState>,
    ): List<WebmentionNotification> =
        repository
            .findByStateInAndDeliveredFalseAndNextAttemptAtNotNullAndNextAttemptAtLessThanEqual(states, now)
            .map { it.toDomain() }

    private fun transitionToPending(
        sourceUrl: String,
        targetUrl: String,
        state: WebmentionState,
    ) {
        val now = Instant.now()
        val entity = findOrCreate(sourceUrl, targetUrl)
        entity.state = state
        entity.delivered = false
        entity.attempts = 0
        entity.lastError = null
        entity.lastStatusCode = null
        entity.lastAttemptAt = null
        entity.nextAttemptAt = now
        entity.updatedAtUtc = now
        repository.save(entity)
    }

    private fun setSilentInactive(
        entity: WebmentionNotificationEntity,
        now: Instant,
    ) {
        entity.state = WebmentionState.INACTIVE
        entity.delivered = false
        entity.attempts = 0
        entity.lastError = null
        entity.nextAttemptAt = null
        entity.updatedAtUtc = now
    }

    private fun applyOutcome(
        sourceUrl: String,
        targetUrl: String,
        block: (WebmentionNotificationEntity, Instant) -> Unit,
    ) {
        val now = Instant.now()
        val entity = findOrCreate(sourceUrl, targetUrl)
        block(entity, now)
        entity.updatedAtUtc = now
        repository.save(entity)
    }

    private fun findOrCreate(
        sourceUrl: String,
        targetUrl: String,
    ): WebmentionNotificationEntity =
        repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)
            ?: WebmentionNotificationEntity(
                sourceUrl = sourceUrl,
                targetUrl = targetUrl,
                state = WebmentionState.ACTIVE,
                createdAtUtc = Instant.now(),
                updatedAtUtc = Instant.now(),
            )
}
