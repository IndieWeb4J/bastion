package dev.jacobandersen.bastion.content.projection

import dev.jacobandersen.bastion.content.event.ContentEventProperties
import dev.jacobandersen.beacon.event.WebmentionEvent
import dev.jacobandersen.conduit.event.SyndicationEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.JetStream
import io.nats.client.JetStreamManagement
import io.nats.client.Message
import io.nats.client.PushSubscribeOptions
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Consumes Beacon's `webmention.*` and Conduit's `syndication.*` streams into
 * Bastion's read-model projections, so the public read API stays a single local
 * query. Durable consumers; failures are redelivered. This is the only place
 * Bastion consumes distribution events, and it is idempotent.
 */
@Component
@ConditionalOnProperty(prefix = "bastion.content.events.nats", name = ["enabled"], havingValue = "true")
class ProjectionConsumer(
    private val connection: Connection,
    private val properties: ContentEventProperties,
    private val webmentionProjectionService: WebmentionProjectionService,
    private val syndicationProjectionService: SyndicationProjectionService,
    private val objectMapper: ObjectMapper,
) {
    private val jetStream: JetStream = connection.jetStream()
    private val management: JetStreamManagement = connection.jetStreamManagement()
    private val dispatchers = mutableListOf<Dispatcher>()

    @PostConstruct
    fun start() {
        val nats = properties.nats
        subscribe<WebmentionEvent>(
            stream = nats.webmentionStream,
            subject = nats.webmentionSubject,
            consumer = nats.webmentionConsumer,
            type = WebmentionEvent::class.java,
        ) { webmentionProjectionService.apply(it) }

        subscribe<SyndicationEvent>(
            stream = nats.syndicationStream,
            subject = nats.syndicationSubject,
            consumer = nats.syndicationConsumer,
            type = SyndicationEvent::class.java,
        ) { syndicationProjectionService.apply(it) }
    }

    @PreDestroy
    fun stop() {
        dispatchers.forEach { connection.closeDispatcher(it) }
    }

    private inline fun <reified T> subscribe(
        stream: String,
        subject: String,
        consumer: String,
        type: Class<T>,
        crossinline apply: (T) -> Unit,
    ) {
        ensureStream(stream, subject)
        val options =
            PushSubscribeOptions
                .builder()
                .stream(stream)
                .durable(consumer)
                .build()
        val handler: (Message) -> Unit = { message ->
            try {
                apply(objectMapper.readValue(String(message.data, Charsets.UTF_8), type))
                message.ack()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to project ${message.subject}; will retry" }
                message.nakWithDelay(Duration.ofSeconds(5))
            }
        }
        dispatchers += connection.createDispatcher().also { d -> jetStream.subscribe(subject, d, handler, false, options) }
        logger.info { "Projecting $subject from $stream (durable=$consumer)" }
    }

    private fun ensureStream(
        stream: String,
        subject: String,
    ) {
        val existing = runCatching { management.getStreamInfo(stream) }.getOrNull()
        if (existing == null) {
            logger.info { "Creating JetStream stream $stream capturing $subject" }
            management.addStream(
                StreamConfiguration
                    .builder()
                    .name(stream)
                    .subjects(subject)
                    .storageType(StorageType.File)
                    .build(),
            )
        } else if (subject !in existing.configuration.subjects) {
            management.updateStream(StreamConfiguration.builder(existing.configuration).addSubjects(subject).build())
        }
    }
}
