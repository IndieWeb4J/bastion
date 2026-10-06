package dev.jacobandersen.bastion.content.media

import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

/** Runs [MediaOrphanSweepService] recurrently when enabled. */
@Component
class MediaOrphanSweepScheduler(
    private val jobScheduler: JobScheduler,
    private val sweepService: MediaOrphanSweepService,
    private val mediaConfiguration: BastionMediaConfiguration,
) {
    @PostConstruct
    fun schedule() {
        if (!mediaConfiguration.orphanSweep.enabled) return
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofHours(INTERVAL_HOURS)) {
            sweepService.sweep()
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "media-orphan-sweep"
        const val INTERVAL_HOURS = 24L
    }
}
