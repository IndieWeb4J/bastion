package dev.jacobandersen.bastion.websub.service

import dev.jacobandersen.bastion.webmention.util.HttpUtil
import dev.jacobandersen.bastion.websub.config.WebsubConfig
import dev.jacobandersen.bastion.websub.http.PublishResult
import dev.jacobandersen.bastion.websub.http.WebsubHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

class WebsubPublishException(
    message: String,
) : RuntimeException(message)

/**
 * Publishes the configured topic to each configured WebSub hub.
 *
 * Dispatch is fire-and-forget: [publish] enqueues one JobRunr job per hub,
 * and JobRunr's exponential backoff retries any job that fails with a
 * retryable error.
 */
@Service
class WebsubPublisher(
    private val jobScheduler: JobScheduler,
    private val httpClient: WebsubHttpClient,
    private val config: WebsubConfig,
) {
    private val hubs: List<String>
        get() = config.hubs.map(String::trim).filter(String::isNotBlank)

    private val enabled: Boolean
        get() = hubs.isNotEmpty() && config.topicUrl.isNotBlank()

    fun publish() {
        if (!enabled) {
            logger.debug { "WebSub publishing disabled (no hubs or topic URL configured)" }
            return
        }

        hubs.forEach { hub -> enqueuePublish(hub) }
    }

    fun publishToHub(hubUrl: String) {
        if (HttpUtil.isBlockedHost(hubUrl, failClosedOnDnsError = false)) {
            logger.warn { "Skipping publish to blocked hub $hubUrl" }
            return
        }

        logger.info { "Publishing WebSub topic ${config.topicUrl} to $hubUrl..." }
        when (val result = httpClient.publish(hubUrl, config.topicUrl)) {
            is PublishResult.Success -> {
                logger.info { "WebSub publish delivered to $hubUrl (HTTP ${result.statusCode})" }
            }

            is PublishResult.Failure -> {
                if (result.retryable) {
                    throw WebsubPublishException("WebSub publish to $hubUrl failed: ${result.message}")
                }
                logger.warn { "WebSub publish to $hubUrl permanently failed: ${result.message}" }
            }
        }
    }

    private fun enqueuePublish(hubUrl: String) {
        jobScheduler.enqueue { publishToHub(hubUrl) }
    }
}
