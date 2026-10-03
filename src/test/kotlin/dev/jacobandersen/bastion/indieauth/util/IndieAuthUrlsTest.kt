package dev.jacobandersen.bastion.indieauth.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndieAuthUrlsTest {
    @Test
    fun `profile urls follow the strict identifier rules`() {
        assertTrue(IndieAuthUrls.isValidProfileUrl("https://example.com/"))
        assertTrue(IndieAuthUrls.isValidProfileUrl("https://example.com/user"))
        assertTrue(IndieAuthUrls.isValidProfileUrl("https://example.com"))
        assertFalse(IndieAuthUrls.isValidProfileUrl("https://example.com:8443/"))
        assertFalse(IndieAuthUrls.isValidProfileUrl("https://172.28.92.51/"))
        assertFalse(IndieAuthUrls.isValidProfileUrl("https://example.com/#me"))
        assertFalse(IndieAuthUrls.isValidProfileUrl("https://user:pass@example.com/"))
        assertFalse(IndieAuthUrls.isValidProfileUrl("https://example.com/foo/../bar"))
    }

    @Test
    fun `client ids allow ports and loopback but not public ips`() {
        assertTrue(IndieAuthUrls.isValidClientId("https://client.example/"))
        assertTrue(IndieAuthUrls.isValidClientId("https://client.example:8443/cb"))
        assertTrue(IndieAuthUrls.isValidClientId("http://127.0.0.1:8080/"))
        assertTrue(IndieAuthUrls.isValidClientId("http://localhost:3000/"))
        assertFalse(IndieAuthUrls.isValidClientId("https://172.28.92.51/"))
        assertFalse(IndieAuthUrls.isValidClientId("https://client.example#frag"))
        assertFalse(IndieAuthUrls.isValidClientId("not-a-url"))
    }

    @Test
    fun `cross-host compares scheme host and port`() {
        assertFalse(IndieAuthUrls.isCrossHost("https://client.example", "https://client.example/callback"))
        assertTrue(IndieAuthUrls.isCrossHost("https://client.example", "https://app.example/callback"))
        assertTrue(IndieAuthUrls.isCrossHost("https://client.example", "http://client.example/callback"))
    }
}
