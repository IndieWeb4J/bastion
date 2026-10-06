package dev.jacobandersen.bastion.content.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private val logger = KotlinLogging.logger {}

/**
 * Drains the content outbox as soon as a write commits, so committed posts do
 * not wait for the safety-net sweep. Failures are swallowed here: the row stays
 * unpublished and [ContentOutboxDrainer]'s recurring sweep retries it.
 */
@Component
class ContentOutboxRelay(
    private val drainer: ContentOutboxDrainer,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOutboxWritten(event: ContentOutboxWritten) {
        try {
            drainer.drain()
        } catch (e: Exception) {
            logger.warn(e) { "Immediate outbox drain failed; the sweep will retry" }
        }
    }
}
