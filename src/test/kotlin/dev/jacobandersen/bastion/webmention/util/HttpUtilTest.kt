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
        assertTrue(HttpUtil.isHtmlContentType(null))
    }

    @Test
    fun rejectsNonHtmlContentTypes() {
        assertFalse(HttpUtil.isHtmlContentType("application/json"))
        assertFalse(HttpUtil.isHtmlContentType("text/plain"))
        assertFalse(HttpUtil.isHtmlContentType("image/png"))
    }

    @Test
    fun detectsLoopbackHosts() {
        assertTrue(HttpUtil.isLoopbackOrLocal("http://localhost/wm"))
        assertTrue(HttpUtil.isLoopbackOrLocal("https://myapp.localhost/wm"))
        assertTrue(HttpUtil.isLoopbackOrLocal("http://127.0.0.1:8080/wm"))
        assertTrue(HttpUtil.isLoopbackOrLocal("http://[::1]/wm"))
    }

    @Test
    fun rejectsNonLoopbackHosts() {
        assertFalse(HttpUtil.isLoopbackOrLocal("https://example.com/wm"))
        assertFalse(HttpUtil.isLoopbackOrLocal("https://203.0.113.1/wm"))
        assertFalse(HttpUtil.isLoopbackOrLocal("https://[2001:db8::1]/wm"))
        assertFalse(HttpUtil.isLoopbackOrLocal("ftp://localhost/wm"))
        assertFalse(HttpUtil.isLoopbackOrLocal("not a url"))
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
