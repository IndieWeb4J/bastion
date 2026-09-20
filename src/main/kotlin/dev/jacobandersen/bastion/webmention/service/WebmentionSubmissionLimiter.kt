package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.webmention.service.WebmentionSubmissionLimiter.Companion.MAX_SUBMISSIONS_PER_SOURCE
import dev.jacobandersen.bastion.webmention.service.WebmentionSubmissionLimiter.Companion.PAIR_COOLDOWN
import dev.jacobandersen.bastion.webmention.service.WebmentionSubmissionLimiter.Companion.SOURCE_WINDOW
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Guards the webmention receiver against submission flooding. The endpoint is
 * unauthenticated, and every accepted submission creates a database row and an
 * asynchronous verification job, so a hostile client could otherwise grow both
 * without bound.
 *
 * Two limits are enforced: a single (source, target) pair is only accepted
 * once per [PAIR_COOLDOWN], and a single source URL may submit at most
 * [MAX_SUBMISSIONS_PER_SOURCE] times per [SOURCE_WINDOW].
 */
@Component
class WebmentionSubmissionLimiter {
    private data class SourceWindow(
        val count: Int,
        val expiresAt: Instant,
    )

    private val lock = Any()
    private val pairCooldowns = LinkedHashMap<String, Instant>()
    private val sourceSubmissions = LinkedHashMap<String, SourceWindow>()

    fun allow(
        sourceUrl: String,
        targetUrl: String,
        now: Instant = Instant.now(),
    ): Boolean {
        synchronized(lock) {
            evictExpired(now)

            val pairKey = "$sourceUrl\u0000$targetUrl"
            val pairLastSeen = pairCooldowns[pairKey]
            if (pairLastSeen != null && pairLastSeen.isAfter(now)) return false

            val window = sourceSubmissions[sourceUrl]
            if (window != null && window.expiresAt.isAfter(now)) {
                if (window.count >= MAX_SUBMISSIONS_PER_SOURCE) return false
                sourceSubmissions[sourceUrl] = window.copy(count = window.count + 1)
            } else {
                sourceSubmissions[sourceUrl] = SourceWindow(count = 1, expiresAt = now.plus(SOURCE_WINDOW))
            }

            pairCooldowns[pairKey] = now.plus(PAIR_COOLDOWN)
            return true
        }
    }

    private fun evictExpired(now: Instant) {
        pairCooldowns.entries.removeIf { !it.value.isAfter(now) }
        sourceSubmissions.entries.removeIf { !it.value.expiresAt.isAfter(now) }
    }

    companion object {
        private val PAIR_COOLDOWN: Duration = Duration.ofSeconds(30)
        private val SOURCE_WINDOW: Duration = Duration.ofHours(1)
        private const val MAX_SUBMISSIONS_PER_SOURCE = 10
    }
}
