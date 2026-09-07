package dev.jacobandersen.bastion.webmention.salmention.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bastion.webmention.salmention")
data class SalmentionConfig(
    val enabled: Boolean = true,
)
