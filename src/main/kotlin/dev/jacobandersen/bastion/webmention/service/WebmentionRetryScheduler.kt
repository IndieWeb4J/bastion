package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class WebmentionRetryScheduler(
    private val jobScheduler: JobScheduler,
    private val webmentionService: WebmentionService,
    private val config: WebmentionConfig,
) {
    @PostConstruct
    fun scheduleRecurringRetry() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofMinutes(config.retryIntervalMinutes)) {
            webmentionService.retryDueWebmentions()
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "webmention-notification-retry"
    }
}
