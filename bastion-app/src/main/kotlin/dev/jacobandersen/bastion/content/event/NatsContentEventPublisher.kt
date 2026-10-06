package dev.jacobandersen.bastion.content.event

import io.github.oshai.kotlinlogging.KotlinLogging
import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamManagement
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import io.nats.client.impl.Headers

private val logger = KotlinLogging.logger {}

/**
 * Publishes content events to the NATS JetStream `CONTENT` stream. The stream
 * is created on first use if absent. JetStream gives at-least-once delivery;
 * consumers dedupe on the per-post `version` carried in the payload.
 */
class NatsContentEventPublisher(
    private val connection: Connection,
    private val streamName: String,
    private val subjectFilter: String,
    private val replicas: Int = 1,
) : ContentEventPublisher,
    AutoCloseable {
    private val jetStream: JetStream = connection.jetStream()
    private val management: JetStreamManagement = connection.jetStreamManagement()

    init {
        ensureStream()
    }

    override fun publish(
        subject: String,
        messageId: String,
        payload: String,
    ) {
        val headers = Headers()
        // A per-event id (the outbox row id) lets JetStream dedupe redeliveries
        // of the same event within its duplicate window; a per-post id would
        // instead collapse distinct events for the same post.
        headers.add("Nats-Msg-Id", messageId)
        val ack = jetStream.publish(subject, headers, payload.toByteArray(Charsets.UTF_8))
        logger.debug { "Published $subject (stream=$streamName, seq=${ack.seqno})" }
    }

    override fun close() {
        connection.close()
    }

    private fun ensureStream() {
        try {
            management.getStreamInfo(streamName)
        } catch (_: Exception) {
            logger.info { "Creating JetStream stream $streamName capturing $subjectFilter" }
            management.addStream(
                StreamConfiguration
                    .builder()
                    .name(streamName)
                    .subjects(subjectFilter)
                    .storageType(StorageType.File)
                    .replicas(replicas)
                    .build(),
            )
        }
    }
}
