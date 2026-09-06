package dev.jacobandersen.bastion.webmention.data.domain

import java.time.Instant

data class WebmentionEndpointCache(
    val targetUrl: String,
    val endpointUrl: String?,
    val discoveredAt: Instant,
    val expiresAt: Instant,
)
