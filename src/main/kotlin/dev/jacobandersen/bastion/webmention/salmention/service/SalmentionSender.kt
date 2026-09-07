package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.webmention.data.service.WebmentionNotificationService
import dev.jacobandersen.bastion.webmention.salmention.config.SalmentionConfig
import dev.jacobandersen.bastion.webmention.service.WebmentionService
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service

/**
 * The Salmention re-send: when a post's displayed responses change, re-send a
 * webmention to every target the post had previously notified (its ACTIVE
 * notifications). This reuses the existing [WebmentionService.sendWebmention]
 * primitive for endpoint discovery/cache, SSRF blocking, delivery and
 * retry/backoff.
 */
@Service
class SalmentionSender(
    private val notificationService: WebmentionNotificationService,
    private val webmentionService: WebmentionService,
    private val jobScheduler: JobScheduler,
    private val config: SalmentionConfig,
) {
    fun resendToActiveTargets(sourceUrl: String) {
        if (!config.enabled) return

        notificationService.activeNotificationsBySource(sourceUrl).forEach { notification ->
            jobScheduler.enqueue { webmentionService.sendWebmention(sourceUrl, notification.targetUrl) }
        }
    }
}
