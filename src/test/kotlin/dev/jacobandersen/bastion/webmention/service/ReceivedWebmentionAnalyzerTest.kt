package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2ParserImpl
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReceivedWebmentionAnalyzerTest {
    private fun analyze(
        html: String,
        baseUrl: String = "https://source.example/post/1",
    ) = ReceivedWebmentionAnalyzer.analyze(Mf2ParserImpl().parse(html, baseUrl))

    @Test
    fun `classifies an in-reply-to as a reply and extracts author`() {
        val analysis =
            analyze(
                """
                <article class="h-entry">
                  <a class="p-author h-card" href="https://alice.example/">
                    <img class="u-photo" alt="" src="https://alice.example/me.jpg"/>Alice
                  </a>
                  <div class="e-content">Great point!</div>
                  <a class="u-in-reply-to" href="https://blog.example/2026/01/01/post">the post</a>
                </article>
                """.trimIndent(),
            )

        assertEquals(WebmentionInteraction.REPLY, analysis.interaction)
        assertEquals("Alice", analysis.authorName)
        assertEquals("https://alice.example/", analysis.authorUrl)
        assertEquals("Great point!", analysis.contentText)
    }

    @Test
    fun `classifies a nested h-cite like-of as a like`() {
        val analysis =
            analyze(
                """
                <div class="h-entry">
                  <p class="e-content">liked</p>
                  <a class="u-like-of h-cite" href="https://blog.example/2026/01/01/post">
                    <span class="p-name">A post</span>
                  </a>
                </div>
                """.trimIndent(),
            )

        assertEquals(WebmentionInteraction.LIKE, analysis.interaction)
    }

    @Test
    fun `classifies an rsvp even when it also replies`() {
        val analysis =
            analyze(
                """
                <div class="h-entry">
                  <a class="u-in-reply-to" href="https://events.example/2026/01/01/party">the party</a>
                  <span class="p-rsvp">yes</span>
                </div>
                """.trimIndent(),
            )

        assertEquals(WebmentionInteraction.RSVP, analysis.interaction)
    }

    @Test
    fun `classifies repost and bookmark properties`() {
        val repost =
            analyze(
                """
                <div class="h-entry">
                  <a class="u-repost-of" href="https://blog.example/2026/01/01/post">repost</a>
                </div>
                """.trimIndent(),
            )
        assertEquals(WebmentionInteraction.REPOST, repost.interaction)

        val bookmark =
            analyze(
                """
                <div class="h-entry">
                  <a class="u-bookmark-of" href="https://blog.example/2026/01/01/post">bookmark</a>
                </div>
                """.trimIndent(),
            )
        assertEquals(WebmentionInteraction.BOOKMARK, bookmark.interaction)
    }

    @Test
    fun `falls back to rel attributes when no h-entry property declares an interaction`() {
        val analysis =
            analyze(
                """
                <div class="h-entry">
                  <p class="e-content">This is my comment on something.</p>
                </div>
                <a rel="in-reply-to" href="https://blog.example/2026/01/01/post">context</a>
                """.trimIndent(),
            )

        assertEquals(WebmentionInteraction.REPLY, analysis.interaction)
    }

    @Test
    fun `a plain mention has no declaring interaction property`() {
        val analysis =
            analyze(
                """
                <div class="h-entry">
                  <div class="e-content">Here is <a href="https://blog.example/2026/01/01/post">a post</a>.</div>
                </div>
                """.trimIndent(),
            )

        assertEquals(WebmentionInteraction.MENTION, analysis.interaction)
        assertEquals("Here is a post.", analysis.contentText)
    }

    @Test
    fun `extracts content html when present`() {
        val analysis =
            analyze(
                """
                <div class="h-entry">
                  <div class="e-content"><b>Hello</b> world</div>
                </div>
                """.trimIndent(),
            )

        assertEquals(WebmentionInteraction.MENTION, analysis.interaction)
        assertEquals("<b>Hello</b> world", analysis.contentHtml)
        assertEquals("Hello world", analysis.contentText)
    }

    @Test
    fun `no microformats at all is still a mention`() {
        val analysis = analyze("""<html><body><p>just prose <a href="https://blog.example/1">link</a></p></body></html>""")
        assertEquals(WebmentionInteraction.MENTION, analysis.interaction)
        assertEquals(null, analysis.primary)
    }
}
