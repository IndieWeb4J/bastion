package dev.jacobandersen.bastion.content

import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2ParserImpl
import dev.jacobandersen.mf24j.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Golden tests for [PostTypeDiscovery], pinning the Kotlin implementation to the
 * behavior of the former PL/pgSQL `post_type_discovery` (see V1/V14). Cases are
 * exercised via parsed mf2 HTML so the mf2 value forms (string, `{value,alt}`,
 * html object) are covered end to end.
 */
class PostTypeDiscoveryTest {
    private val parser = Mf2ParserImpl()

    private fun entry(propertiesHtml: String): Mf2Object {
        val html = """<div class="h-entry">$propertiesHtml</div>"""
        return parser.parse(html, "https://example.com/").items.single()
    }

    private fun discover(propertiesHtml: String): String? = PostTypeDiscovery.discover(entry(propertiesHtml))

    @Test
    fun `classifies an rsvp by the presence of its value`() {
        assertEquals("rsvp", discover("""<span class="p-rsvp">yes</span>"""))
    }

    @Test
    fun `classifies an in-reply-to url as a reply`() {
        assertEquals(
            "reply",
            discover("""<a class="u-in-reply-to" href="https://example.com/a">a</a><div class="e-content">A comment</div>"""),
        )
    }

    @Test
    fun `a reply with a photo is still a reply`() {
        assertEquals(
            "reply",
            discover(
                """<a class="u-in-reply-to" href="https://example.com/a">a</a><img class="u-photo" src="https://example.com/pic.jpg"/><div class="e-content">reply</div>""",
            ),
        )
    }

    @Test
    fun `classifies reposts and likes`() {
        assertEquals("repost", discover("""<a class="u-repost-of" href="https://example.com/a">a</a>"""))
        assertEquals("like", discover("""<a class="u-like-of" href="https://example.com/a">a</a>"""))
    }

    @Test
    fun `classifies video posts`() {
        assertEquals("video", discover("""<video class="u-video" src="https://example.com/clip.mp4"></video>"""))
    }

    @Test
    fun `classifies a photo`() {
        assertEquals("photo", discover("""<img class="u-photo" src="https://example.com/pic.jpg"/>"""))
    }

    @Test
    fun `note and article classification from plain content`() {
        assertEquals("note", discover("""<div class="e-content">Hello world</div><span class="p-name">Hello</span>"""))
        assertEquals("article", discover("""<div class="e-content">A much longer post body</div><span class="p-name">My title</span>"""))
    }

    @Test
    fun `a post without content is a note`() {
        assertEquals("note", discover("""<span class="p-name">Only a name</span>"""))
    }

    @Test
    fun `uses summary as content fallback`() {
        assertEquals("note", discover("""<span class="p-summary">Hello all</span><span class="p-name">Hello</span>"""))
    }

    @Test
    fun `classifies a bookmark by bookmark-of`() {
        assertEquals(
            "bookmark",
            discover("""<a class="u-bookmark-of" href="https://example.com/a">a</a><div class="e-content">Look at this</div>"""),
        )
    }

    @Test
    fun `classifies a checkin by checkin property`() {
        assertEquals(
            "checkin",
            discover(
                """<span class="p-checkin"><span class="h-card"><span class="p-name">Blue Bottle</span></span></span>""",
            ),
        )
    }

    @Test
    fun `classifies a mood by mood property`() {
        assertEquals("mood", discover("""<span class="p-mood">happy</span><div class="e-content">Feeling good</div>"""))
    }

    @Test
    fun `a plain note still discovers as a note`() {
        assertEquals("note", discover("""<div class="e-content">Just a thought</div>"""))
    }

    @Test
    fun `a non h-entry object has no discovered post type`() {
        val card = parser.parse("""<div class="h-card"><span class="p-name">Sally</span></div>""", "https://example.com/").items.single()
        assertNull(PostTypeDiscovery.discover(card))
    }

    @Test
    fun `categories are normalized deduped and sorted`() {
        val obj =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mapOf(
                        "category" to
                            listOf(
                                Mf2Value.String(" Kotlin "),
                                Mf2Value.String("kotlin"),
                                Mf2Value.String("Web"),
                            ),
                    ),
            )
        assertEquals(listOf("kotlin", "web"), PostTypeDiscovery.categories(obj))
    }
}
