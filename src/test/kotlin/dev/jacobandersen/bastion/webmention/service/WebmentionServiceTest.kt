package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionNotification
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.service.EndpointCacheResult
import dev.jacobandersen.bastion.webmention.data.service.WebmentionEndpointCacheService
import dev.jacobandersen.bastion.webmention.data.service.WebmentionNotificationService
import dev.jacobandersen.bastion.webmention.http.EndpointDiscovery
import dev.jacobandersen.bastion.webmention.http.SendWebmentionResult
import dev.jacobandersen.bastion.webmention.http.WebmentionHttpClient
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.time.Instant
import java.util.UUID

class WebmentionServiceTest {
    private val jobScheduler = mock(JobScheduler::class.java)
    private val notificationService = mock(WebmentionNotificationService::class.java)
    private val endpointCacheService = mock(WebmentionEndpointCacheService::class.java)
    private val httpClient = mock(WebmentionHttpClient::class.java)
    private val config = WebmentionConfig()

    private val service = WebmentionService(jobScheduler, notificationService, endpointCacheService, httpClient, config)

    private val source = "https://bastion.test/2026/09/07/post"
    private val target = "https://example.com/a"

    private fun entry(content: String): Mf2Object =
        Mf2Object(
            type = listOf("h-entry"),
            properties = mapOf("content" to listOf(Mf2Value.String(content))),
            children = null,
        )

    private fun notification(
        targetUrl: String,
        delivered: Boolean = true,
    ) = WebmentionNotification(
        id = UUID.randomUUID(),
        sourceUrl = source,
        targetUrl = targetUrl,
        state = WebmentionState.ACTIVE,
        delivered = delivered,
        createdAtUtc = Instant.now(),
        updatedAtUtc = Instant.now(),
    )

    @Test
    fun `processWebmentions enqueues sends for unknown targets`() {
        `when`(notificationService.notification(source, target)).thenReturn(null)

        service.processWebmentions(source, entry("see $target"))

        verify(notificationService, times(1)).setActivePending(source, target)
    }

    @Test
    fun `processWebmentions leaves already-active targets alone`() {
        `when`(notificationService.notification(source, target)).thenReturn(notification(target))

        service.processWebmentions(source, entry("see $target"))

        verify(notificationService, never()).setActivePending(source, target)
    }

    @Test
    fun `updateReSendsUnchangedDeliveredTargetsAndSendsNewTargets`() {
        val unchanged = "https://example.com/a"
        val added = "https://example.com/b"
        `when`(notificationService.notification(source, unchanged)).thenReturn(notification(unchanged))
        `when`(notificationService.notification(source, added)).thenReturn(null)

        service.processUpdatedWebmentions(source, listOf(unchanged), entry("$unchanged $added"))

        verify(notificationService, times(1)).setActivePending(source, unchanged)
        verify(notificationService, times(1)).setActivePending(source, added)
    }

    @Test
    fun `updateRetractsRemovedDeliveredTargets`() {
        val kept = "https://example.com/a"
        val removed = "https://example.com/c"
        `when`(notificationService.notification(source, kept)).thenReturn(notification(kept))
        `when`(notificationService.notification(source, removed)).thenReturn(notification(removed))

        service.processUpdatedWebmentions(source, listOf(kept, removed), entry(kept))

        verify(notificationService, times(1)).markInactivePendingRetraction(source, removed)
    }

    @Test
    fun `updateSilentlyInactivatesRemovedUndeliveredTargets`() {
        val kept = "https://example.com/a"
        val removed = "https://example.com/c"
        `when`(notificationService.notification(source, kept)).thenReturn(notification(kept))
        `when`(notificationService.notification(source, removed)).thenReturn(notification(removed, delivered = false))

        service.processUpdatedWebmentions(source, listOf(kept, removed), entry(kept))

        verify(notificationService, times(1)).markInactiveSilent(source, removed)
    }

    @Test
    fun `processDeletedWebmentions retracts delivered and silently inactivates pending`() {
        val delivered = notification("https://example.com/a")
        val pending = notification("https://example.com/b", delivered = false)
        `when`(notificationService.activeNotificationsBySource(source)).thenReturn(listOf(delivered, pending))

        service.processDeletedWebmentions(source)

        verify(notificationService, times(1)).markInactivePendingRetraction(source, "https://example.com/a")
        verify(notificationService, times(1)).markInactiveSilent(source, "https://example.com/b")
    }

    @Test
    fun `deactivateWebmentions inactivates everything for the source`() {
        service.deactivateWebmentions(source)

        verify(notificationService, times(1)).inactivateAllBySource(source)
    }

    @Test
    fun `retryDueWebmentions enqueues sends for due notifications`() {
        val due = notification("https://example.com/a")
        `when`(notificationService.dueForRetry(any(), any())).thenReturn(listOf(due))

        service.retryDueWebmentions()

        verify(notificationService, times(1))
            .dueForRetry(any(), org.mockito.kotlin.eq(listOf(WebmentionState.ACTIVE, WebmentionState.INACTIVE)))
    }

    @Test
    fun `sendWebmention skips blocked targets`() {
        service.sendWebmention(source, "http://127.0.0.1/internal")

        verify(
            notificationService,
            times(1),
        ).recordFailure(source, "http://127.0.0.1/internal", null, "target URL resolves to a blocked address")
        verify(notificationService, times(1)).scheduleNextAttempt(source, "http://127.0.0.1/internal", null)
        verify(
            httpClient,
            never(),
        ).sendWebmention(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
        )
    }

    @Test
    fun `sendWebmention records terminal failure when no endpoint is advertised`() {
        `when`(endpointCacheService.lookup(org.mockito.ArgumentMatchers.anyString(), any()))
            .thenReturn(EndpointCacheResult.Miss)
        `when`(httpClient.discoverWebmentionEndpoint(target)).thenReturn(EndpointDiscovery(null, null, null))

        service.sendWebmention(source, target)

        verify(notificationService, times(1)).recordFailure(source, target, null, "no webmention endpoint advertised")
        verify(notificationService, times(1)).scheduleNextAttempt(source, target, null)
    }

    @Test
    fun `sendWebmention uses a fresh cached endpoint`() {
        `when`(endpointCacheService.lookup(org.mockito.kotlin.eq(target), any()))
            .thenReturn(EndpointCacheResult.Fresh("https://example.com/wm"))
        `when`(httpClient.sendWebmention(source, target, "https://example.com/wm"))
            .thenReturn(SendWebmentionResult.Success(202))

        service.sendWebmention(source, target)

        verify(notificationService, times(1)).recordSuccess(source, target, 202)
        verify(httpClient, never()).discoverWebmentionEndpoint(target)
    }

    @Test
    fun `sendWebmention schedules retry with backoff for transient failures`() {
        `when`(endpointCacheService.lookup(org.mockito.kotlin.eq(target), any()))
            .thenReturn(EndpointCacheResult.Fresh("https://example.com/wm"))
        `when`(httpClient.sendWebmention(source, target, "https://example.com/wm"))
            .thenReturn(SendWebmentionResult.Failure(503, "HTTP 503", retryable = true))
        `when`(notificationService.recordFailure(source, target, 503, "HTTP 503")).thenReturn(1)

        service.sendWebmention(source, target)

        verify(notificationService, times(1)).scheduleNextAttempt(
            org.mockito.kotlin.eq(source),
            org.mockito.kotlin.eq(target),
            org.mockito.kotlin.check { next ->
                assertEquals(config.backoffBaseSeconds.toDouble(), (next.epochSecond - Instant.now().epochSecond).toDouble(), 2.0)
            },
        )
    }

    @Test
    fun `sendWebmention stops retrying after the max attempts`() {
        `when`(endpointCacheService.lookup(org.mockito.kotlin.eq(target), any()))
            .thenReturn(EndpointCacheResult.Fresh("https://example.com/wm"))
        `when`(httpClient.sendWebmention(source, target, "https://example.com/wm"))
            .thenReturn(SendWebmentionResult.Failure(503, "HTTP 503", retryable = true))
        `when`(notificationService.recordFailure(source, target, 503, "HTTP 503")).thenReturn(config.maxAttempts)

        service.sendWebmention(source, target)

        verify(notificationService, times(1)).scheduleNextAttempt(source, target, null)
    }

    @Test
    fun `sendWebmention does not retry non-retryable failures`() {
        `when`(endpointCacheService.lookup(org.mockito.kotlin.eq(target), any()))
            .thenReturn(EndpointCacheResult.Fresh("https://example.com/wm"))
        `when`(httpClient.sendWebmention(source, target, "https://example.com/wm"))
            .thenReturn(SendWebmentionResult.Failure(400, "HTTP 400", retryable = false))
        `when`(notificationService.recordFailure(source, target, 400, "HTTP 400")).thenReturn(1)

        service.sendWebmention(source, target)

        verify(notificationService, times(1)).scheduleNextAttempt(source, target, null)
    }
}
