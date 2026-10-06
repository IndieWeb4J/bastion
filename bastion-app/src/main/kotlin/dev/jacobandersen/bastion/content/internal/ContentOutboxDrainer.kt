package dev.jacobandersen.bastion.content.internal

import dev.jacobandersen.bastion.content.event.ContentEventPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Publishes the content outbox to the bus, then marks each row published. Runs
 * transactionally per batch; a crash between publish and mark yields an
 * at-most-once redelivery which consumers absorb via the per-post version
 * guard. Scheduled recurrently through JobRunr.
 */
@Service
class ContentOutboxDrainer(
    private val repository: ContentOutboxRepository,
    private val publisher: ContentEventPublisher,
    private val jobScheduler: JobScheduler,
) {
    @PostConstruct
    fun schedule() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofSeconds(INTERVAL_SECONDS)) {
            drain()
        }
    }

    @Transactional
    fun drain() {
        val pending = repository.findTop100ByPublishedAtUtcIsNullOrderByCreatedAtUtcAsc()
        if (pending.isEmpty()) return
        for (row in pending) {
            publisher.publish(row.subject, row.partitionKey, row.payload)
            row.publishedAtUtc = Instant.now()
            repository.save(row)
        }
        logger.debug { "Drained ${pending.size} content outbox row(s)" }
    }

    companion object {
        const val RECURRING_JOB_ID = "content-outbox-drainer"
        const val INTERVAL_SECONDS = 5L
    }
}
