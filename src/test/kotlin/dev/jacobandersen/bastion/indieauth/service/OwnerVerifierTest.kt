package dev.jacobandersen.bastion.indieauth.service

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnerVerifierTest {
    private fun document(html: String) = Jsoup.parse(html, "https://bastion.test/")

    @Test
    fun `recognizes a rel me anchor matching the profile`() {
        val doc = document("""<a href="https://github.com/someone" rel="me">GitHub</a>""")
        assertTrue(OwnerVerifier.hasRelMeLink(doc, "https://github.com/someone"))
    }

    @Test
    fun `recognizes a rel me link element matching the profile`() {
        val doc = document("""<link href="https://github.com/someone" rel="me">""")
        assertTrue(OwnerVerifier.hasRelMeLink(doc, "https://github.com/someone"))
    }

    @Test
    fun `matches a profile with a trailing slash`() {
        val doc = document("""<a href="https://github.com/someone/" rel="me">GitHub</a>""")
        assertTrue(OwnerVerifier.hasRelMeLink(doc, "https://github.com/someone"))
    }

    @Test
    fun `matches rel containing me alongside other values`() {
        val doc = document("""<a href="https://github.com/someone" rel="nofollow me">GitHub</a>""")
        assertTrue(OwnerVerifier.hasRelMeLink(doc, "https://github.com/someone"))
    }

    @Test
    fun `rejects a rel me link to a different profile`() {
        val doc = document("""<a href="https://github.com/attacker" rel="me">GitHub</a>""")
        assertFalse(OwnerVerifier.hasRelMeLink(doc, "https://github.com/someone"))
    }

    @Test
    fun `rejects a link whose rel is not me`() {
        val doc = document("""<a href="https://github.com/someone" rel="author">GitHub</a>""")
        assertFalse(OwnerVerifier.hasRelMeLink(doc, "https://github.com/someone"))
    }

    @Test
    fun `rejects an invalid profile url`() {
        val doc = document("""<a href="https://github.com/someone" rel="me">GitHub</a>""")
        assertFalse(OwnerVerifier.hasRelMeLink(doc, "not-a-url"))
    }
}
