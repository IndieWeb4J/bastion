package dev.jacobandersen.bastion.webmention.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WebmentionUtilTest {
    @Test
    fun matchesQuotedRel() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/wm>; rel=\"webmention\""))

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun matchesUnquotedRel() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/wm>; rel=webmention"))

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun matchesRelAmongOtherTokens() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/wm>; rel=\"webmention nofollow\""))

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun ignoresRelCase() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/wm>; rel=\"WebMention\""))

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun matchesRelRegardlessOfParamOrder() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/wm>; type=\"text/html\"; rel=\"webmention\""))

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun findsWebmentionAcrossMultipleLinkValuesInOneHeader() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/a>; rel=\"preload\", <https://example.com/wm>; rel=\"webmention\""))

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun findsWebmentionAcrossMultipleHeaders() {
        val headers = listOf(
            "<https://example.com/a>; rel=\"preload\"",
            "<https://example.com/wm>; rel=\"webmention\"",
        )

        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(headers)

        assertEquals("https://example.com/wm", endpoint)
    }

    @Test
    fun prefersFirstWebmentionHeader() {
        val headers = listOf(
            "<https://example.com/first>; rel=\"webmention\"",
            "<https://example.com/second>; rel=\"webmention\"",
        )

        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(headers)

        assertEquals("https://example.com/first", endpoint)
    }

    @Test
    fun doesNotSplitOnCommaInsideAngleBrackets() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/a,b>; rel=\"webmention\""))

        assertEquals("https://example.com/a,b", endpoint)
    }

    @Test
    fun returnsNullWhenNoWebmentionRel() {
        val endpoint = WebmentionUtil.findEndpointInLinkHeaders(listOf("<https://example.com/a>; rel=\"next\"", "<https://example.com/b>"))

        assertNull(endpoint)
    }

    @Test
    fun resolvesRelativeEndpoint() {
        assertEquals("https://example.com/webmention", WebmentionUtil.resolveEndpoint("/webmention", "https://example.com/post"))
    }

    @Test
    fun resolvesEndpointAgainstBasePath() {
        assertEquals("https://example.com/a/webmention", WebmentionUtil.resolveEndpoint("webmention", "https://example.com/a/b"))
    }

    @Test
    fun resolvesRelativeEndpointPreservingQuery() {
        assertEquals(
            "https://example.com/webmention?token=abc",
            WebmentionUtil.resolveEndpoint("/webmention?token=abc", "https://example.com/post"),
        )
    }

    @Test
    fun leavesAbsoluteEndpointUntouched() {
        val endpoint = "https://wm.example.com/endpoint?token=abc"

        assertEquals(endpoint, WebmentionUtil.resolveEndpoint(endpoint, "https://example.com/post"))
    }

    @Test
    fun resolvesProtocolRelativeEndpoint() {
        assertEquals("https://wm.example.com/endpoint", WebmentionUtil.resolveEndpoint("//wm.example.com/endpoint", "https://example.com/post"))
    }

    @Test
    fun returnsNullForUnresolvableEndpoint() {
        assertNull(WebmentionUtil.resolveEndpoint("not a valid uri with spaces", "https://example.com/post"))
    }

    @Test
    fun expiryUsesMaxAge() {
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val expiry = WebmentionUtil.computeDiscoveryExpiry(
            cacheControl = "public, max-age=1200",
            expiresHeader = null,
            now = now,
            defaultTtlSeconds = 3600,
            minCacheSeconds = 300,
        )

        assertEquals(now.plusSeconds(1200), expiry)
    }

    @Test
    fun expiryFloorsShortMaxAge() {
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val expiry = WebmentionUtil.computeDiscoveryExpiry(
            cacheControl = "max-age=0",
            expiresHeader = null,
            now = now,
            defaultTtlSeconds = 3600,
            minCacheSeconds = 300,
        )

        assertEquals(now.plusSeconds(300), expiry)
    }

    @Test
    fun expiryUsesExpiresHeader() {
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val expiry = WebmentionUtil.computeDiscoveryExpiry(
            cacheControl = null,
            expiresHeader = "Thu, 01 Jan 2026 02:00:00 GMT",
            now = now,
            defaultTtlSeconds = 3600,
            minCacheSeconds = 300,
        )

        assertEquals(now.plusSeconds(7200), expiry)
    }

    @Test
    fun expiryFallsBackToDefault() {
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val expiry = WebmentionUtil.computeDiscoveryExpiry(
            cacheControl = null,
            expiresHeader = null,
            now = now,
            defaultTtlSeconds = 3600,
            minCacheSeconds = 300,
        )

        assertEquals(now.plusSeconds(3600), expiry)
    }
}
