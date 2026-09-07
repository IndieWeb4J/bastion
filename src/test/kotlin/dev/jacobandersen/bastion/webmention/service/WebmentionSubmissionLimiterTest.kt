package dev.jacobandersen.bastion.webmention.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class WebmentionSubmissionLimiterTest {
    private val limiter = WebmentionSubmissionLimiter()
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `allows the first submission for a pair`() {
        assertTrue(limiter.allow("https://a.example", "https://b.example", now))
    }

    @Test
    fun `rejects a repeated pair within the cooldown`() {
        assertTrue(limiter.allow("https://a.example", "https://b.example", now))
        assertFalse(limiter.allow("https://a.example", "https://b.example", now.plusSeconds(10)))
    }

    @Test
    fun `allows a repeated pair after the cooldown expires`() {
        assertTrue(limiter.allow("https://a.example", "https://b.example", now))
        assertTrue(limiter.allow("https://a.example", "https://b.example", now.plusSeconds(31)))
    }

    @Test
    fun `allows distinct targets from one source up to the per-source cap`() {
        val source = "https://a.example"
        repeat(10) { index ->
            assertTrue(limiter.allow(source, "https://target-$index.example", now), "submission $index should pass")
        }
    }

    @Test
    fun `rejects submissions from a source beyond the per-source cap`() {
        val source = "https://a.example"
        repeat(10) { index -> limiter.allow(source, "https://target-$index.example", now) }

        assertFalse(limiter.allow(source, "https://target-10.example", now))
    }

    @Test
    fun `resets the per-source cap when the window expires`() {
        val source = "https://a.example"
        repeat(10) { index -> limiter.allow(source, "https://target-$index.example", now) }

        assertTrue(limiter.allow(source, "https://target-11.example", now.plusSeconds(3601)))
    }

    @Test
    fun `expired entries do not accumulate`() {
        repeat(50) { index ->
            limiter.allow("https://source-$index.example", "https://target.example", now.minusSeconds(60))
        }
        // All prior entries are expired; fresh submissions are allowed.
        assertTrue(limiter.allow("https://new.example", "https://target.example", now))
    }
}
