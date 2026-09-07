package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionNotification
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.service.WebmentionEndpointCacheService
import dev.jacobandersen.bastion.webmention.data.service.WebmentionNotificationService
import dev.jacobandersen.bastion.webmention.http.WebmentionHttpClient
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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

    private fun entry(content: String): Mf2Object =
        Mf2Object(
            type = listOf("h-entry"),
            properties = mutableMapOf("content" to listOf(Mf2Value.String(content))),
            children = null,
        )

    private fun notification(
        targetUrl: String,
        delivered: Boolean = true,
    ): WebmentionNotification =
        WebmentionNotification(
            id = UUID.randomUUID(),
            sourceUrl = source,
            targetUrl = targetUrl,
            state = WebmentionState.ACTIVE,
            delivered = delivered,
            createdAtUtc = Instant.now(),
            updatedAtUtc = Instant.now(),
        )

    @Test
    fun updateReSendsUnchangedDeliveredTargetsAndSendsNewTargets() {
        val unchanged = "https://example.com/a"
        val added = "https://example.com/b"
        `when`(notificationService.notification(source, unchanged)).thenReturn(notification(unchanged))
        `when`(notificationService.notification(source, added)).thenReturn(null)

        service.processUpdatedWebmentions(source, listOf(unchanged), entry("$unchanged $added"))

        verify(notificationService, times(1)).setActivePending(source, unchanged)
        verify(notificationService, times(1)).setActivePending(source, added)
    }

    @Test
    fun updateRetractsRemovedDeliveredTargets() {
        val kept = "https://example.com/a"
        val removed = "https://example.com/c"
        `when`(notificationService.notification(source, kept)).thenReturn(notification(kept))
        `when`(notificationService.notification(source, removed)).thenReturn(notification(removed))

        service.processUpdatedWebmentions(source, listOf(kept, removed), entry(kept))

        verify(notificationService, times(1)).markInactivePendingRetraction(source, removed)
    }
}
