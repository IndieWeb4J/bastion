package dev.jacobandersen.content.client.autoconfigure

import dev.jacobandersen.content.client.ContentClient
import dev.jacobandersen.content.client.ContentClientProperties
import dev.jacobandersen.content.client.ContentReadClient
import dev.jacobandersen.content.client.ContentWriteClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import tools.jackson.databind.ObjectMapper

/**
 * Registers a [ContentClient] plus its read/write interfaces. Activated when
 * `content.client.base-url` is set. Every bean is [ConditionalOnMissingBean].
 */
@AutoConfiguration
@EnableConfigurationProperties(ContentClientProperties::class)
@ConditionalOnProperty(prefix = "content.client", name = ["base-url"])
class ContentClientAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun contentClient(
        properties: ContentClientProperties,
        objectMapper: ObjectMapper,
    ): ContentClient = ContentClient(properties, objectMapper)

    @Bean
    @ConditionalOnMissingBean
    fun contentReadClient(contentClient: ContentClient): ContentReadClient = contentClient

    @Bean
    @ConditionalOnMissingBean
    fun contentWriteClient(contentClient: ContentClient): ContentWriteClient = contentClient
}
