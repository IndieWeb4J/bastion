package dev.jacobandersen.bastion.content

import dev.jacobandersen.bastion.content.event.ContentEventProperties
import dev.jacobandersen.bastion.content.event.ContentEventPublisher
import dev.jacobandersen.bastion.content.event.NatsContentEventPublisher
import dev.jacobandersen.bastion.content.event.NoopContentEventPublisher
import io.nats.client.Nats
import io.nats.client.Options
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the content event publisher: a NATS JetStream publisher when
 * `bastion.content.events.nats.enabled=true`, otherwise a no-op so local
 * deployments and tests work before (or without) a broker. Either way the
 * Postgres outbox holds the durable record until drained.
 */
@Configuration
@EnableConfigurationProperties(ContentEventProperties::class)
class ContentEventConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ContentEventPublisher::class)
    @ConditionalOnProperty(prefix = "bastion.content.events.nats", name = ["enabled"], havingValue = "true")
    fun natsContentEventPublisher(properties: ContentEventProperties): ContentEventPublisher {
        val nats = properties.nats
        val connection = Nats.connect(Options.builder().server(nats.url).build())
        return NatsContentEventPublisher(connection, nats.contentStream, nats.contentSubject)
    }

    @Bean
    @ConditionalOnMissingBean(ContentEventPublisher::class)
    fun noopContentEventPublisher(): ContentEventPublisher = NoopContentEventPublisher
}
