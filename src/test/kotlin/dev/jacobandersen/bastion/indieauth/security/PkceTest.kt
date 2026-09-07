package dev.jacobandersen.bastion.indieauth.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PkceTest {
    @Test
    fun `s256 produces a url-safe challenge`() {
        val challenge = Pkce.s256("a-verifier")

        assertTrue(challenge.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(challenge.contains('='))
    }

    @Test
    fun `verify accepts a matching verifier`() {
        val verifier = "a-random-verifier"

        assertTrue(Pkce.verify(Pkce.s256(verifier), verifier))
    }

    @Test
    fun `verify rejects a mismatching verifier`() {
        assertFalse(Pkce.verify(Pkce.s256("verifier"), "a-different-verifier"))
    }
}
