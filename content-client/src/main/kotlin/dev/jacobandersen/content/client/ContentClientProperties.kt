package dev.jacobandersen.content.client

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Connection settings for the Bastion content service. When [baseUrl] is set,
 * the Spring Boot auto-configuration registers a ready-to-use [ContentClient].
 */
@ConfigurationProperties(prefix = "content.client")
data class ContentClientProperties(
    /** Base URL of the content service, e.g. `https://bastion.example.com`. */
    val baseUrl: String = "",
    /** Shared service token presented as a bearer credential for internal calls. */
    val serviceToken: String = "",
    /** TCP connect timeout. */
    val connectTimeout: Duration = Duration.ofSeconds(3),
    /** Read timeout. */
    val readTimeout: Duration = Duration.ofSeconds(3),
)
