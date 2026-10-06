package dev.jacobandersen.bastion.content.event

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Content event transport configuration. Events go onto NATS JetStream when
 * [Nats.enabled] is set; otherwise a no-op publisher is used so local runs and
 * tests work without a broker (the Postgres outbox is the durable source).
 */
@ConfigurationProperties(prefix = "bastion.content.events")
data class ContentEventProperties(
    val nats: Nats = Nats(),
) {
    data class Nats(
        val enabled: Boolean = false,
        val url: String = "nats://localhost:4222",
        /** The JetStream stream content events are published to. */
        val contentStream: String = "CONTENT",
        /** The subject filter the stream captures. */
        val contentSubject: String = "content.>",
    )
}
