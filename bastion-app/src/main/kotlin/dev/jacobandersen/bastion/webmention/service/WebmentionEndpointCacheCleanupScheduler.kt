package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.webmention.data.service.WebmentionEndpointCacheService
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Periodically removes expired webmention endpoint cache entries so the cache
 * stays bounded. Since entries are only stored when the target advertises
 * cacheability, this also drains legacy rows once their window passes.
 */
@Component
class WebmentionEndpointCacheCleanupScheduler(
    private val jobScheduler: JobScheduler,
    private val endpointCacheService: WebmentionEndpointCacheService,
) {
    @PostConstruct
    fun scheduleCleanup() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofHours(CLEANUP_INTERVAL_HOURS)) {
            endpointCacheService.purgeExpired(Instant.now())
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "webmention-endpoint-cache-cleanup"
        const val CLEANUP_INTERVAL_HOURS = 6L
    }
}
