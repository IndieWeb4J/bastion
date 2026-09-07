package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Periodically runs [IndieAuthRowPurgeService.purge] so the IndieAuth
 * authorization-request, authorization-code and access-token tables stay
 * bounded. Mirrors the recurring-job pattern used by the webmention schedulers.
 */
@Component
class IndieAuthRowPurgeScheduler(
    private val jobScheduler: JobScheduler,
    private val rowPurgeService: IndieAuthRowPurgeService,
    private val config: IndieAuthConfig,
) {
    @PostConstruct
    fun schedulePurge() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, config.purgeInterval) {
            rowPurgeService.purge(Instant.now())
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "indieauth-row-purge"
    }
}
