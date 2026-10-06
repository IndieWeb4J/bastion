package dev.jacobandersen.bastion.webmention.data.domain

import java.time.Instant
import java.util.UUID

data class WebmentionNotification(
    val id: UUID,
    val sourceUrl: String,
    val targetUrl: String,
    val state: WebmentionState,
    val delivered: Boolean = false,
    val attempts: Int = 0,
    val lastError: String? = null,
    val lastStatusCode: Int? = null,
    val lastAttemptAt: Instant? = null,
    val nextAttemptAt: Instant? = null,
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant,
)
