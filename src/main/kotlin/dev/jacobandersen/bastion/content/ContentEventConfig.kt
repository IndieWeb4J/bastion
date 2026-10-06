package dev.jacobandersen.bastion.content

import dev.jacobandersen.bastion.content.event.ContentEventPublisher
import dev.jacobandersen.bastion.content.event.NoopContentEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the content event publisher. Overridden by a NATS-backed implementation
 * when the bus is configured; defaults to a no-op so local deployments and tests
 * work before the broker exists.
 */
@Configuration
class ContentEventConfig {
    @Bean
    @ConditionalOnMissingBean(ContentEventPublisher::class)
    fun contentEventPublisher(): ContentEventPublisher = NoopContentEventPublisher
}
