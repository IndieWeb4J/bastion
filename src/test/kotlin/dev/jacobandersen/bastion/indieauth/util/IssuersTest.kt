package dev.jacobandersen.bastion.indieauth.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IssuersTest {
    @Test
    fun `root public url becomes a trailing-slash issuer`() {
        assertEquals("https://bastion.test/", Issuers.issuer("https://bastion.test"))
    }

    @Test
    fun `trailing slash is preserved as a single slash`() {
        assertEquals("https://bastion.test/", Issuers.issuer("https://bastion.test/"))
    }

    @Test
    fun `issuer with a path keeps the path without a trailing slash`() {
        assertEquals("https://example.com/sub", Issuers.issuer("https://example.com/sub/"))
    }

    @Test
    fun `non-https public url is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Issuers.issuer("http://bastion.test") }
    }

    @Test
    fun `public url with a query is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Issuers.issuer("https://bastion.test/?x=1") }
    }

    @Test
    fun `base url trims the trailing slash`() {
        assertEquals("https://bastion.test", Issuers.baseUrl("https://bastion.test/"))
    }
}
