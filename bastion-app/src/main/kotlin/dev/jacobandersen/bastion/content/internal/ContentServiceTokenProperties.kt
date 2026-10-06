package dev.jacobandersen.bastion.content.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Shared secret for the internal content API, presented by services (Forge,
 * Beacon, Conduit) as a bearer credential. Blank disables the internal API.
 */
@ConfigurationProperties(prefix = "bastion.content.internal")
data class ContentServiceTokenProperties(
    val token: String = "",
)
