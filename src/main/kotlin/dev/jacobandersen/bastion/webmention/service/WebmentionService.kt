package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.url.UrlExtractor
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
import dev.jacobandersen.bastion.webmention.data.service.EndpointCacheResult
import dev.jacobandersen.bastion.webmention.data.service.WebmentionEndpointCacheService
import dev.jacobandersen.bastion.webmention.data.service.WebmentionNotificationService
import dev.jacobandersen.bastion.webmention.http.EndpointDiscovery
import dev.jacobandersen.bastion.webmention.http.SendWebmentionResult
import dev.jacobandersen.bastion.webmention.http.WebmentionHttpClient
import dev.jacobandersen.bastion.webmention.util.HttpUtil
import dev.jacobandersen.bastion.webmention.util.Mf2TextExtractor
import dev.jacobandersen.bastion.webmention.util.WebmentionUtil
import dev.jacobandersen.mf24j.Mf2Object
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class WebmentionService(
    private val jobScheduler: JobScheduler,
    private val notificationService: WebmentionNotificationService,
    private val endpointCacheService: WebmentionEndpointCacheService,
    private val httpClient: WebmentionHttpClient,
    private val config: WebmentionConfig,
    private val urlService: UrlService,
) {
    fun processWebmentions(
        sourceUrl: String,
        obj: Mf2Object,
    ) {
        targetUrlsOf(obj).forEach { target ->
            enqueueIfInactive(sourceUrl, target)
        }
    }

    fun processUpdatedWebmentions(
        sourceUrl: String,
        previousTargetUrls: Collection<String>,
        obj: Mf2Object,
    ) {
        val previous = previousTargetUrls.toSet()
        val current = targetUrlsOf(obj)

        val new = current - previous
        new.forEach { target ->
            enqueueIfInactive(sourceUrl, target)
        }

        val unchanged = previous.intersect(current)
        unchanged.forEach { target ->
            val existing = notificationService.notification(sourceUrl, target)
            if (existing != null && (existing.state == WebmentionState.ACTIVE || existing.delivered)) {
                notificationService.setActivePending(sourceUrl, target)
                enqueueSend(sourceUrl, target, forceRediscovery = true)
            }
        }

        val removed = previous - current
        removed.forEach { target ->
            val existing = notificationService.notification(sourceUrl, target)
            if (existing == null || existing.state != WebmentionState.ACTIVE) return@forEach

            if (existing.delivered) {
                notificationService.markInactivePendingRetraction(sourceUrl, target)
                enqueueSend(sourceUrl, target, forceRediscovery = true)
            } else {
                notificationService.markInactiveSilent(sourceUrl, target)
            }
        }
    }

    fun processDeletedWebmentions(sourceUrl: String) {
        notificationService.activeNotificationsBySource(sourceUrl).forEach { notification ->
            if (notification.delivered) {
                notificationService.markInactivePendingRetraction(sourceUrl, notification.targetUrl)
                enqueueSend(sourceUrl, notification.targetUrl, forceRediscovery = true)
            } else {
                notificationService.markInactiveSilent(sourceUrl, notification.targetUrl)
            }
        }
    }

    fun deactivateWebmentions(sourceUrl: String) {
        notificationService.inactivateAllBySource(sourceUrl)
    }

    fun retryDueWebmentions() {
        val states = listOf(WebmentionState.ACTIVE, WebmentionState.INACTIVE)
        notificationService.dueForRetry(Instant.now(), states).forEach { notification ->
            enqueueSend(notification.sourceUrl, notification.targetUrl)
        }
    }

    fun sendWebmention(
        sourceUrl: String,
        targetUrl: String,
        forceRediscovery: Boolean = false,
    ) {
        logger.info { "Sending webmention for $sourceUrl to $targetUrl..." }

        if (urlService.isOwnContentUrl(targetUrl)) {
            logger.info { "Skipping self-webmention to $targetUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "self webmention skipped")
            return
        }

        if (HttpUtil.isBlockedHost(targetUrl, failClosedOnDnsError = false)) {
            logger.info { "Skipping webmention to blocked target $targetUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "target URL resolves to a blocked address")
            return
        }

        val endpointUrl = resolveEndpointForTarget(targetUrl, forceRediscovery)
        if (endpointUrl == null) {
            logger.info { "No remote webmention endpoint found for $targetUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "no webmention endpoint advertised")
            return
        }

        if (HttpUtil.isBlockedHost(endpointUrl, failClosedOnDnsError = false)) {
            logger.info { "Skipping webmention to blocked endpoint $endpointUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "webmention endpoint resolves to a blocked address")
            return
        }

        when (val result = httpClient.sendWebmention(sourceUrl, targetUrl, endpointUrl)) {
            is SendWebmentionResult.Success -> {
                logger.info { "Webmention delivered to $endpointUrl (HTTP ${result.statusCode})" }
                notificationService.recordSuccess(sourceUrl, targetUrl, result.statusCode)
            }

            is SendWebmentionResult.Failure -> {
                logger.warn { "Webmention to $endpointUrl failed: ${result.message}" }
                val attempts =
                    notificationService.recordFailure(sourceUrl, targetUrl, result.statusCode, result.message)
                val nextAttempt =
                    if (result.retryable && attempts < config.maxAttempts) {
                        nextAttemptAt(attempts)
                    } else {
                        null
                    }
                notificationService.scheduleNextAttempt(sourceUrl, targetUrl, nextAttempt)
            }
        }
    }

    internal fun targetUrlsOf(obj: Mf2Object): Set<String> {
        val urls = UrlExtractor.distinctUrls(Mf2TextExtractor.extractText(obj))
        return urls.filterNot(urlService::isOwnContentUrl).toSet()
    }

    private fun resolveEndpointForTarget(
        targetUrl: String,
        forceRediscovery: Boolean = false,
    ): String? {
        val now = Instant.now()

        if (!forceRediscovery) {
            when (val cached = endpointCacheService.lookup(targetUrl, now)) {
                is EndpointCacheResult.Fresh -> return cached.endpointUrl
                is EndpointCacheResult.Miss -> Unit
            }
        }

        val discovery = discover(targetUrl)
        val expiresAt =
            WebmentionUtil.effectiveCacheExpiry(
                cacheControl = discovery.cacheControl,
                expiresHeader = discovery.expiresHeader,
                now = now,
            )
        if (expiresAt != null) {
            endpointCacheService.store(targetUrl, discovery.endpointUrl, expiresAt)
        } else {
            endpointCacheService.evict(targetUrl)
        }
        return discovery.endpointUrl
    }

    private fun discover(targetUrl: String): EndpointDiscovery = httpClient.discoverWebmentionEndpoint(targetUrl)

    private fun nextAttemptAt(attempts: Int): Instant {
        val exponent = (attempts - 1).coerceAtLeast(0)
        val seconds =
            minOf(
                config.backoffBaseSeconds * (1L shl exponent.coerceAtMost(20)),
                config.backoffMaxSeconds,
            )
        return Instant.now().plusSeconds(seconds)
    }

    private fun recordTerminalFailure(
        sourceUrl: String,
        targetUrl: String,
        reason: String,
    ) {
        notificationService.recordFailure(sourceUrl, targetUrl, null, reason)
        notificationService.scheduleNextAttempt(sourceUrl, targetUrl, null)
    }

    private fun enqueueIfInactive(
        sourceUrl: String,
        targetUrl: String,
    ) {
        val current = notificationService.notification(sourceUrl, targetUrl)
        if (current == null || current.state != WebmentionState.ACTIVE) {
            notificationService.setActivePending(sourceUrl, targetUrl)
            enqueueSend(sourceUrl, targetUrl)
        }
    }

    private fun enqueueSend(
        sourceUrl: String,
        targetUrl: String,
        forceRediscovery: Boolean = false,
    ) {
        jobScheduler.enqueue { sendWebmention(sourceUrl, targetUrl, forceRediscovery) }
    }
}
