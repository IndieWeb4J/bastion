package dev.jacobandersen.bastion.content.url

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UrlNormalizerTest {
    @Test
    fun `authority lowercases scheme and host and drops default ports`() {
        assertEquals(
            UrlNormalizer.Authority("https", "example.com", -1),
            UrlNormalizer.authority("HTTPS://EXAMPLE.COM:443/path"),
        )
        assertEquals(
            UrlNormalizer.Authority("http", "example.com", -1),
            UrlNormalizer.authority("http://example.com:80"),
        )
        assertEquals(
            UrlNormalizer.Authority("https", "example.com", 8443),
            UrlNormalizer.authority("https://example.com:8443"),
        )
    }

    @Test
    fun `authority rejects non-http schemes and invalid urls`() {
        assertNull(UrlNormalizer.authority("ftp://example.com"))
        assertNull(UrlNormalizer.authority("not a url"))
    }

    @Test
    fun `identity trims trailing slashes and drops fragments and queries`() {
        assertEquals(
            "https://example.com/profile",
            UrlNormalizer.identity("https://example.com/profile/"),
        )
        assertEquals(
            "https://example.com/profile",
            UrlNormalizer.identity("HTTPS://EXAMPLE.COM/profile#frag"),
        )
        assertEquals(
            "https://example.com/profile",
            UrlNormalizer.identity("https://example.com/profile?utm_source=x"),
        )
    }

    @Test
    fun `identity preserves explicit non-default ports`() {
        assertEquals("https://example.com:8443/profile", UrlNormalizer.identity("https://example.com:8443/profile"))
        assertEquals("https://example.com/profile", UrlNormalizer.identity("https://example.com:443/profile"))
    }

    @Test
    fun `dedupKey canonicalizes the query and drops tracking parameters`() {
        assertEquals(
            "example.com/post?a=1&b=2",
            UrlNormalizer.dedupKey("https://example.com/post?b=2&a=1&utm_medium=social"),
        )
        assertEquals("example.com/post", UrlNormalizer.dedupKey("https://example.com/post?"))
    }

    @Test
    fun `dedupKey normalizes the path`() {
        assertEquals(
            "example.com/post",
            UrlNormalizer.dedupKey("https://example.com/sub/../post"),
        )
        assertEquals("example.com/post", UrlNormalizer.dedupKey("https://example.com/post/"))
    }
}
