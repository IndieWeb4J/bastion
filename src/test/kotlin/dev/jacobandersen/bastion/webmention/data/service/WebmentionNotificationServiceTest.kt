package dev.jacobandersen.bastion.webmention.data.service

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.entity.WebmentionNotificationEntity
import dev.jacobandersen.bastion.webmention.data.repository.WebmentionNotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class WebmentionNotificationServiceTest {
    private val repository = mock(WebmentionNotificationRepository::class.java)
    private val service = WebmentionNotificationService(repository)

    private val sourceUrl = "https://bastion.test/2026/01/01/post"
    private val targetUrl = "https://example.com/target"

    private fun entity(
        state: WebmentionState = WebmentionState.ACTIVE,
        delivered: Boolean = false,
        attempts: Int = 0,
        nextAttemptAt: Instant? = null,
    ) = WebmentionNotificationEntity(
        sourceUrl = sourceUrl,
        targetUrl = targetUrl,
        state = state,
        delivered = delivered,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        createdAtUtc = Instant.now(),
        updatedAtUtc = Instant.now(),
    )

    @Test
    fun `setActivePending creates a new active pending notification`() {
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(null)

        service.setActivePending(sourceUrl, targetUrl)

        val captor = ArgumentCaptor.forClass(WebmentionNotificationEntity::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertEquals(WebmentionState.ACTIVE, saved.state)
        assertFalse(saved.delivered)
        assertNotNull(saved.nextAttemptAt)
        assertNotNull(saved.createdAtUtc)
    }

    @Test
    fun `markInactivePendingRetraction flips an existing notification to pending retraction`() {
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(entity(delivered = true))

        service.markInactivePendingRetraction(sourceUrl, targetUrl)

        val captor = ArgumentCaptor.forClass(WebmentionNotificationEntity::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertEquals(WebmentionState.INACTIVE, saved.state)
        assertFalse(saved.delivered)
        assertEquals(0, saved.attempts)
        assertNotNull(saved.nextAttemptAt)
    }

    @Test
    fun `markInactiveSilent clears scheduling state without retraction`() {
        val existing = entity(state = WebmentionState.ACTIVE, delivered = true)
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(existing)

        service.markInactiveSilent(sourceUrl, targetUrl)

        assertEquals(WebmentionState.INACTIVE, existing.state)
        assertFalse(existing.delivered)
        assertNull(existing.nextAttemptAt)
        verify(repository).save(existing)
    }

    @Test
    fun `inactivateAllBySource only touches active notifications`() {
        val active = entity()
        `when`(repository.findBySourceUrlAndState(sourceUrl, WebmentionState.ACTIVE)).thenReturn(listOf(active))

        service.inactivateAllBySource(sourceUrl)

        assertEquals(WebmentionState.INACTIVE, active.state)
        verify(repository, times(1)).findBySourceUrlAndState(sourceUrl, WebmentionState.ACTIVE)
    }

    @Test
    fun `recordSuccess marks delivered and clears retry state`() {
        val existing = entity(delivered = false, attempts = 3, nextAttemptAt = Instant.now().plusSeconds(60))
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(existing)

        service.recordSuccess(sourceUrl, targetUrl, 202)

        assertTrue(existing.delivered)
        assertEquals(0, existing.attempts)
        assertNull(existing.lastError)
        assertEquals(202, existing.lastStatusCode)
        assertNull(existing.nextAttemptAt)
        verify(repository).save(existing)
    }

    @Test
    fun `recordFailure increments attempts and returns the new count`() {
        val existing = entity(delivered = false, attempts = 1)
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(existing)

        val attempts = service.recordFailure(sourceUrl, targetUrl, 500, "boom")

        assertEquals(2, attempts)
        assertEquals(2, existing.attempts)
        assertEquals("boom", existing.lastError)
        assertEquals(500, existing.lastStatusCode)
        verify(repository).save(existing)
    }

    @Test
    fun `scheduleNextAttempt updates timing without touching delivery state`() {
        val existing = entity(delivered = false, attempts = 1)
        val next = Instant.now().plusSeconds(30)
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(existing)

        service.scheduleNextAttempt(sourceUrl, targetUrl, next)

        assertEquals(next, existing.nextAttemptAt)
        assertFalse(existing.delivered)
        assertEquals(1, existing.attempts)
        verify(repository).save(existing)
    }

    @Test
    fun `scheduleNextAttempt ignores unknown pairs`() {
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(null)

        service.scheduleNextAttempt(sourceUrl, targetUrl, Instant.now())

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any())
    }

    @Test
    fun `notification returns null for unknown pairs`() {
        `when`(repository.findBySourceUrlAndTargetUrl(sourceUrl, targetUrl)).thenReturn(null)

        assertNull(service.notification(sourceUrl, targetUrl))
    }

    @Test
    fun `dueForRetry delegates to the repository with the due date`() {
        val now = Instant.now()
        val due =
            entity(state = WebmentionState.ACTIVE, nextAttemptAt = now.minusSeconds(1)).apply { id = UUID.randomUUID() }
        `when`(
            repository.findByStateInAndDeliveredFalseAndNextAttemptAtNotNullAndNextAttemptAtLessThanEqual(
                org.mockito.kotlin.eq(listOf(WebmentionState.ACTIVE, WebmentionState.INACTIVE)),
                org.mockito.kotlin.eq(now),
            ),
        ).thenReturn(listOf(due))

        val result = service.dueForRetry(now, listOf(WebmentionState.ACTIVE, WebmentionState.INACTIVE))

        assertEquals(1, result.size)
        assertEquals(sourceUrl, result[0].sourceUrl)
    }

    @Test
    fun `activeNotificationsBySource maps entities to domain`() {
        val now = Instant.now()
        val saved = entity()
        saved.id = UUID.randomUUID()
        `when`(repository.findBySourceUrlAndState(sourceUrl, WebmentionState.ACTIVE)).thenReturn(listOf(saved))

        val result = service.activeNotificationsBySource(sourceUrl)

        assertEquals(1, result.size)
        val domain = result[0]
        assertEquals(saved.id, domain.id)
        assertEquals(WebmentionState.ACTIVE, domain.state)
    }
}
