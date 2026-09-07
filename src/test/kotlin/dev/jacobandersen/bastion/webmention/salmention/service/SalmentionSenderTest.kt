package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionNotification
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.service.WebmentionNotificationService
import dev.jacobandersen.bastion.webmention.salmention.config.SalmentionConfig
import dev.jacobandersen.bastion.webmention.service.WebmentionService
import org.jobrunr.jobs.lambdas.JobLambda
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.time.Instant
import java.util.UUID

class SalmentionSenderTest {
    private val notificationService = mock(WebmentionNotificationService::class.java)
    private val webmentionService = mock(WebmentionService::class.java)
    private val jobScheduler = mock(JobScheduler::class.java)

    private val source = "https://bastion.test/2026/09/07/post"

    private fun sender(enabled: Boolean = true) =
        SalmentionSender(notificationService, webmentionService, jobScheduler, SalmentionConfig(enabled = enabled))

    private fun notification(targetUrl: String) =
        WebmentionNotification(
            id = UUID.randomUUID(),
            sourceUrl = source,
            targetUrl = targetUrl,
            state = WebmentionState.ACTIVE,
            delivered = true,
            createdAtUtc = Instant.now(),
            updatedAtUtc = Instant.now(),
        )

    @Test
    fun `enqueues a send for every active target of the source`() {
        val targets = listOf(notification("https://example.com/a"), notification("https://example.com/b"))
        `when`(notificationService.activeNotificationsBySource(source)).thenReturn(targets)

        sender().resendToActiveTargets(source)

        verify(jobScheduler, times(2)).enqueue(any<JobLambda>())
    }

    @Test
    fun `does nothing when the source has no active targets`() {
        `when`(notificationService.activeNotificationsBySource(source)).thenReturn(emptyList())

        sender().resendToActiveTargets(source)

        verify(jobScheduler, never()).enqueue(any<JobLambda>())
    }

    @Test
    fun `does nothing when salmention is disabled`() {
        val targets = listOf(notification("https://example.com/a"))
        `when`(notificationService.activeNotificationsBySource(source)).thenReturn(targets)

        sender(enabled = false).resendToActiveTargets(source)

        verify(notificationService, never()).activeNotificationsBySource(source)
    }
}
