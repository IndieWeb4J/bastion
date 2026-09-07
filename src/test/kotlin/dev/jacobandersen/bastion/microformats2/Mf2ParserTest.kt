package dev.jacobandersen.bastion.microformats2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Mf2ParserTest {
    private val parser = Mf2ParserImpl()

    private fun singleItem(
        html: String,
        baseUrl: String = "http://example.com",
    ): Mf2Object {
        val result = parser.parse(html, baseUrl)
        assertEquals(1, result.items.size, "expected a single microformat item")
        return result.items.single()
    }

    private fun Mf2Object.firstString(key: String): String? = (getProperty(key).firstOrNull() as? Mf2Value.String)?.value

    private fun Mf2Object.first(key: String): Mf2Value? = getProperty(key).firstOrNull()

    @Test
    fun `parses a simple hyperlinked h-card with implied name and url`() {
        val card = singleItem("""<a class="h-card" href="http://benward.me">Ben Ward</a>""")

        assertEquals(listOf("h-card"), card.type)
        assertEquals("Ben Ward", card.firstString("name"))
        assertEquals("http://benward.me", card.firstString("url"))
    }

    @Test
    fun `parses explicit properties of a detailed h-card`() {
        val card =
            singleItem(
                """
                <div class="h-card">
                  <img class="u-photo" alt="photo of Mitchell" src="http://blog.lizardwrangler.com/pic.png"/>
                  <a class="p-name u-url" href="http://blog.lizardwrangler.com/">Mitchell Baker</a>
                  <span class="p-org">Mozilla Foundation</span>
                </div>
                """.trimIndent(),
            )

        assertEquals("Mitchell Baker", card.firstString("name"))
        assertEquals(listOf("http://blog.lizardwrangler.com/"), card.strings("url"))
        val photo = card.first("photo") as Mf2Value.Json
        assertEquals("http://blog.lizardwrangler.com/pic.png", photo.value.get("value").asText())
        assertEquals("photo of Mitchell", photo.value.get("alt").asText())
        assertEquals("Mozilla Foundation", card.firstString("org"))
    }

    @Test
    fun `attaches a same-element nested microformat as a property value`() {
        val card =
            singleItem(
                """
                <div class="h-card">
                  <a class="p-name u-url" href="http://blog.lizardwrangler.com/">Mitchell Baker</a>
                  (<a class="p-org h-card" href="http://mozilla.org/">Mozilla Foundation</a>)
                </div>
                """.trimIndent(),
            )

        val org = card.first("org") as Mf2Value.Object
        assertEquals(listOf("h-card"), org.value.type)
        assertEquals("Mozilla Foundation", org.value.firstString("name"))
        assertEquals("http://mozilla.org/", org.value.firstString("url"))
    }

    @Test
    fun `parses an h-entry with a nested h-cite like-of`() {
        val entry =
            singleItem(
                """
                <div class="h-entry">
                  <p class="e-content">I liked this post.</p>
                  <a class="u-like-of h-cite" href="https://example.com/post">
                    <span class="p-name">Example post</span>
                  </a>
                </div>
                """.trimIndent(),
            )

        assertEquals(listOf("h-entry"), entry.type)
        val likeOf = entry.first("like-of") as Mf2Value.Object
        assertEquals(listOf("h-cite"), likeOf.value.type)
        assertEquals("Example post", likeOf.value.firstString("name"))
        assertEquals("https://example.com/post", likeOf.value.firstString("url"))
    }

    @Test
    fun `parses value-class-pattern date and time into a combined datetime`() {
        val event =
            singleItem(
                """
                <div class="h-event">
                  <span class="p-name">Party</span>
                  <span class="dt-start">
                    <time class="value" datetime="2008-06-24">this Tuesday</time> at
                    <time class="value">18:30</time>
                  </span>
                </div>
                """.trimIndent(),
            )

        assertEquals("Party", event.firstString("name"))
        assertEquals("2008-06-24 18:30", event.firstString("start"))
    }

    @Test
    fun `adopts a missing date from a previous dt property in the same microformat`() {
        val event =
            singleItem(
                """
                <div class="h-event">
                  <span class="p-name">Party</span>
                  <span class="dt-start"><time class="value" datetime="2009-06-26">26 Jun</time></span>
                  <time class="dt-end">22:00</time>
                </div>
                """.trimIndent(),
            )

        assertEquals("2009-06-26 22:00", event.firstString("end"))
    }

    @Test
    fun `e-content is stored as an html value object`() {
        val entry =
            singleItem(
                """
                <div class="h-entry">
                  <div class="e-content"><b>Hello</b> <i>World</i></div>
                </div>
                """.trimIndent(),
            )

        val content = entry.first("content") as Mf2Value.Json
        assertEquals("<b>Hello</b> <i>World</i>", content.value.get("html").asText())
        assertEquals("Hello World", content.value.get("value").asText())
    }

    @Test
    fun `parses classic hentry with nested vcard author`() {
        val result =
            parser.parse(
                """
                <div class="hentry">
                  <h1 class="entry-title">My post</h1>
                  <span class="entry-content">Some content</span>
                  <span class="author vcard">
                    <a class="fn url" href="http://example.com/alice">Alice</a>
                  </span>
                </div>
                """.trimIndent(),
                "http://example.com/2016/01/01/my-post",
            )

        assertEquals(1, result.items.size)
        val entry = result.items.single()
        assertEquals(listOf("h-entry"), entry.type)
        assertEquals("My post", entry.firstString("name"))

        val author = entry.first("author") as Mf2Value.Object
        assertEquals(listOf("h-card"), author.value.type)
        assertEquals("Alice", author.value.firstString("name"))
        assertEquals("http://example.com/alice", author.value.firstString("url"))
    }

    @Test
    fun `parses rel microformats including rel-urls details`() {
        val result =
            parser.parse(
                """
                <a rel="author" href="http://example.com/a">author a</a>
                <a rel="in-reply-to" href="http://example.com/1">post 1</a>
                <a rel="alternate home" href="http://example.com/fr" media="handheld" hreflang="fr">French mobile homepage</a>
                """.trimIndent(),
                "http://example.com/",
            )

        assertEquals(listOf("http://example.com/a"), result.rels["author"])
        assertEquals(listOf("http://example.com/1"), result.rels["in-reply-to"])
        assertEquals(listOf("http://example.com/fr"), result.rels["alternate"])

        val relUrl = result.relUrls.getValue("http://example.com/fr")
        assertEquals(listOf("alternate", "home"), relUrl.rels)
        assertEquals("handheld", relUrl.media)
        assertEquals("fr", relUrl.hreflang)
    }

    @Test
    fun `bare nested microformats become children`() {
        val result =
            parser.parse(
                """
                <div class="h-feed">
                  <div class="h-entry">
                    <span class="p-name">One</span>
                  </div>
                  <div class="h-entry">
                    <span class="p-name">Two</span>
                  </div>
                </div>
                """.trimIndent(),
                "http://example.com",
            )

        val feed = result.items.single()
        assertEquals(listOf("h-feed"), feed.type)
        assertEquals(2, feed.children?.size)
        assertEquals("One", feed.children!![0].firstString("name"))
    }

    private fun Mf2Object.strings(key: String): List<String> = getProperty(key).mapNotNull { (it as? Mf2Value.String)?.value }
}
