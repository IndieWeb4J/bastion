package dev.jacobandersen.bastion.webmention.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpUtilTest {
    @Test
    fun recognizesHtmlContentTypes() {
        assertTrue(HttpUtil.isHtmlContentType("text/html"))
        assertTrue(HttpUtil.isHtmlContentType("text/html; charset=utf-8"))
        assertTrue(HttpUtil.isHtmlContentType("application/xhtml+xml"))
        assertTrue(HttpUtil.isHtmlContentType("TEXT/HTML"))
        assertTrue(HttpUtil.isHtmlContentType(null))
    }

    @Test
    fun rejectsNonHtmlContentTypes() {
        assertFalse(HttpUtil.isHtmlContentType("application/json"))
        assertFalse(HttpUtil.isHtmlContentType("text/plain"))
        assertFalse(HttpUtil.isHtmlContentType("image/png"))
    }

    @Test
    fun blocksLoopbackAndLocalHosts() {
        assertTrue(HttpUtil.isBlockedHost("http://localhost/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("https://myapp.localhost/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://127.0.0.1:8080/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://[::1]/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://0.0.0.0/wm", failClosedOnDnsError = true))
    }

    @Test
    fun blocksPrivateAndReservedRanges() {
        assertTrue(HttpUtil.isBlockedHost("http://10.0.0.1/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://172.16.0.1/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://192.168.1.1/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://169.254.169.254/latest/meta-data", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://[fd00::1]/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("http://[fe80::1]/wm", failClosedOnDnsError = true))
    }

    @Test
    fun allowsPublicHosts() {
        assertFalse(HttpUtil.isBlockedHost("https://example.com/wm", failClosedOnDnsError = true))
        assertFalse(HttpUtil.isBlockedHost("https://203.0.113.1/wm", failClosedOnDnsError = true))
        assertFalse(HttpUtil.isBlockedHost("https://[2001:db8::1]/wm", failClosedOnDnsError = true))
    }

    @Test
    fun blocksNonHttpSchemesAndInvalidUrls() {
        assertTrue(HttpUtil.isBlockedHost("ftp://localhost/wm", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("file:///etc/passwd", failClosedOnDnsError = true))
        assertTrue(HttpUtil.isBlockedHost("not a url", failClosedOnDnsError = true))
    }

    @Test
    fun unresolvableHostsFollowFailClosedPolicy() {
        assertTrue(HttpUtil.isBlockedHost("http://definitely-does-not-exist.invalid/wm", failClosedOnDnsError = true))
        assertFalse(HttpUtil.isBlockedHost("http://definitely-does-not-exist.invalid/wm", failClosedOnDnsError = false))
    }

    @Test
    fun classifiesTransientStatuses() {
        assertTrue(HttpUtil.isTransientStatus(408))
        assertTrue(HttpUtil.isTransientStatus(425))
        assertTrue(HttpUtil.isTransientStatus(429))
        assertTrue(HttpUtil.isTransientStatus(500))
        assertTrue(HttpUtil.isTransientStatus(503))
    }

    @Test
    fun classifiesPermanentStatuses() {
        assertFalse(HttpUtil.isTransientStatus(400))
        assertFalse(HttpUtil.isTransientStatus(404))
        assertFalse(HttpUtil.isTransientStatus(410))
        assertFalse(HttpUtil.isTransientStatus(200))
    }
}
