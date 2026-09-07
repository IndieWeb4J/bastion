package dev.jacobandersen.bastion.websub.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * WebSub (PubSubHubbub) publisher configuration.
 *
 * Bastion is a WebSub publisher only: when a post is created, updated,
 * deleted or undeleted it pings each configured hub with `hub.mode=publish`
 * and the topic URL, so hubs re-fetch the feed. Bastion never acts as a
 * WebSub subscriber.
 *
 * @property hubs The hub URLs to notify. An empty list disables publishing.
 * @property topicUrl The topic (feed) URL a hub re-fetches after a publish
 *   ping. Bastion is API-only and does not itself serve a feed document, so
 *   this is an operator-supplied URL that some external system renders as the
 *   feed. A blank value disables publishing.
 * @property connectTimeoutSeconds Outbound connect timeout for hub pings.
 * @property readTimeoutSeconds Outbound read timeout for hub pings.
 */
@ConfigurationProperties(prefix = "bastion.websub")
data class WebsubConfig(
    val hubs: List<String> = emptyList(),
    val topicUrl: String = "",
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 10,
)
