package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.webmention.http.SourceFetch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebmentionSourceVerifierTest {

    private val target = "https://blog.example/2026/01/01/post"

    private fun html(body: String, status: Int = 200, type: String = "text/html") = SourceFetch(
        statusCode = status,
        finalUrl = "https://source.example/reply",
        contentType = type,
        body = body,
    )

    @Test
    fun `verifies an html source that links to the target`() {
        val result = WebmentionSourceVerifier.verify(
            html("""<div class="h-entry"><p>see <a href="$target">this</a></p></div>"""),
            target,
        )
        assertEquals(SourceVerdict.VERIFIED, result.verdict)
        assertNotNull(result.parse)
    }

    @Test
    fun `rejects an html source that does not link to the target`() {
        val result = WebmentionSourceVerifier.verify(
            html("""<div class="h-entry"><p>no links here</p></div>"""),
            target,
        )
        assertEquals(SourceVerdict.NO_LINK, result.verdict)
    }

    @Test
    fun `matches an img src as a mention of the target`() {
        val result = WebmentionSourceVerifier.verify(
            html("""<div class="h-entry"><img src="$target" alt="post"/></div>"""),
            target,
        )
        assertEquals(SourceVerdict.VERIFIED, result.verdict)
    }

    @Test
    fun `a link that differs only by a fragment still mentions the target`() {
        val result = WebmentionSourceVerifier.verify(
            html("""<a href="$target#reply-1">reply</a>"""),
            target,
        )
        assertEquals(SourceVerdict.VERIFIED, result.verdict)
    }

    @Test
    fun `a gone source marks the webmention deleted`() {
        val gone = WebmentionSourceVerifier.verify(html("gone", status = 410), target)
        assertEquals(SourceVerdict.GONE, gone.verdict)

        val notFound = WebmentionSourceVerifier.verify(html("missing", status = 404), target)
        assertEquals(SourceVerdict.GONE, notFound.verdict)
    }

    @Test
    fun `an unreachable source is flagged`() {
        val result = WebmentionSourceVerifier.verify(
            SourceFetch(0, target, null, "", error = "connection refused"),
            target,
        )
        assertEquals(SourceVerdict.UNREACHABLE, result.verdict)
        assertEquals("connection refused", result.reason)
    }

    @Test
    fun `plain text sources are checked by substring`() {
        val matching = WebmentionSourceVerifier.verify(
            SourceFetch(200, "https://source.example/note", "text/plain", "Reading $target today"),
            target,
        )
        assertEquals(SourceVerdict.VERIFIED, matching.verdict)
        assertNull(matching.parse)

        val notMatching = WebmentionSourceVerifier.verify(
            SourceFetch(200, "https://source.example/note", "text/plain", "nothing here"),
            target,
        )
        assertEquals(SourceVerdict.NO_LINK, notMatching.verdict)
    }
}
