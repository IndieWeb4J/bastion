package dev.jacobandersen.bastion.content.event

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Content event transport configuration. Events go onto NATS JetStream when
 * [Nats.enabled] is set; otherwise a no-op publisher is used so local runs and
 * tests work without a broker (the Postgres outbox is the durable source).
 * The same connection is used to consume the distribution streams Bastion
 * projects into its read model.
 */
@ConfigurationProperties(prefix = "bastion.content.events")
data class ContentEventProperties(
    val nats: Nats = Nats(),
) {
    data class Nats(
        val enabled: Boolean = false,
        val url: String = "nats://localhost:4222",
        /** JetStream replica count for streams this service creates (1 dev, 3 prod). */
        val replicas: Int = 1,
        /** The JetStream stream content events are published to. */
        val contentStream: String = "CONTENT",
        /** The subject filter the content stream captures. */
        val contentSubject: String = "content.>",
        /** Beacon's stream/subject Bastion projects webmentions from. */
        val webmentionStream: String = "WEBMENTION",
        val webmentionSubject: String = "webmention.>",
        val webmentionConsumer: String = "bastion-webmention-projection",
        /** Conduit's stream/subject Bastion projects syndications from. */
        val syndicationStream: String = "SYNDICATION",
        val syndicationSubject: String = "syndication.>",
        val syndicationConsumer: String = "bastion-syndication-projection",
    )
}
