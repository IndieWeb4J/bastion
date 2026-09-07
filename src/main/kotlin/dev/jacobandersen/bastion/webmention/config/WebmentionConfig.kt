package dev.jacobandersen.bastion.webmention.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bastion.webmention")
data class WebmentionConfig(
    val retryIntervalMinutes: Long = 30,
    val maxAttempts: Int = 5,
    val backoffBaseSeconds: Long = 1800,
    val backoffMaxSeconds: Long = 86400,
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 10,
)
