package dev.jacobandersen.bastion.content

import dev.jacobandersen.bastion.content.event.ContentEventProperties
import dev.jacobandersen.bastion.content.event.ContentEventPublisher
import dev.jacobandersen.bastion.content.event.NatsContentEventPublisher
import dev.jacobandersen.bastion.content.event.NoopContentEventPublisher
import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the content event transport: a NATS JetStream publisher (and the
 * connection the projection consumers share) when
 * `bastion.content.events.nats.enabled=true`, otherwise a no-op publisher so
 * local deployments and tests work before (or without) a broker. Either way the
 * Postgres outbox holds the durable record until drained.
 */
@Configuration
@EnableConfigurationProperties(ContentEventProperties::class)
class ContentEventConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "bastion.content.events.nats", name = ["enabled"], havingValue = "true")
    fun natsConnection(properties: ContentEventProperties): Connection = Nats.connect(Options.builder().server(properties.nats.url).build())

    @Bean
    @ConditionalOnMissingBean(ContentEventPublisher::class)
    @ConditionalOnProperty(prefix = "bastion.content.events.nats", name = ["enabled"], havingValue = "true")
    fun natsContentEventPublisher(
        connection: Connection,
        properties: ContentEventProperties,
    ): ContentEventPublisher = NatsContentEventPublisher(connection, properties.nats.contentStream, properties.nats.contentSubject)

    @Bean
    @ConditionalOnMissingBean(ContentEventPublisher::class)
    fun noopContentEventPublisher(): ContentEventPublisher = NoopContentEventPublisher
}
