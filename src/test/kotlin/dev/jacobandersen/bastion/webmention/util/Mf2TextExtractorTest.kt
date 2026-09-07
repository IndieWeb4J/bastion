package dev.jacobandersen.bastion.webmention.util

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class Mf2TextExtractorTest {
    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun json(value: String) = Mf2Value.Json(mapper.readTree(value))

    private fun entry(properties: MutableMap<String, List<Mf2Value>>) = Mf2Object(
        type = listOf("h-entry"),
        properties = properties,
        children = null,
    )

    @Test
    fun ignoresNonHEntry() {
        val obj = Mf2Object(
            type = listOf("h-card"),
            properties = mutableMapOf("name" to listOf(Mf2Value.String("Jane"))),
            children = null,
        )

        assertEquals(emptyList<String>(), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun extractsStringContent() {
        val obj = entry(mutableMapOf("content" to listOf(Mf2Value.String("Hello world"))))

        assertEquals(listOf("Hello world"), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun extractsBothValueAndHtmlFromJsonContent() {
        val obj = entry(
            mutableMapOf(
                "content" to listOf(json("""{"value":"Read this","html":"<p>Read <a href=\"https://example.com\">this</a></p>"}""")),
            )
        )

        assertEquals(
            listOf("Read this", """<p>Read <a href="https://example.com">this</a></p>"""),
            Mf2TextExtractor.extractText(obj),
        )
    }

    @Test
    fun extractsHtmlOnlyWhenValueMissing() {
        val obj = entry(mutableMapOf("content" to listOf(json("""{"html":"<p>Hi</p>"}"""))))

        assertEquals(listOf("<p>Hi</p>"), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun extractsValueOnlyWhenHtmlMissing() {
        val obj = entry(mutableMapOf("content" to listOf(json("""{"value":"Hi"}"""))))

        assertEquals(listOf("Hi"), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun extractsSummaryButNotName() {
        val obj = entry(
            mutableMapOf(
                "name" to listOf(Mf2Value.String("Title with https://example.com")),
                "summary" to listOf(Mf2Value.String("A summary")),
            )
        )

        assertEquals(listOf("A summary"), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun extractsReferencePropertyString() {
        val obj = entry(mutableMapOf("like-of" to listOf(Mf2Value.String("https://example.com/post"))))

        assertEquals(listOf("https://example.com/post"), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun extractsUrlAndContentFromNestedCite() {
        val cite = Mf2Value.Object(
            Mf2Object(
                type = listOf("h-cite"),
                properties = mutableMapOf(
                    "url" to listOf(Mf2Value.String("https://example.com/post")),
                    "name" to listOf(Mf2Value.String("A great post")),
                    "content" to listOf(json("""{"value":"Quote","html":"<blockquote>Quote</blockquote>"}""")),
                ),
                children = null,
            )
        )

        val obj = entry(mutableMapOf("in-reply-to" to listOf(cite)))

        assertEquals(
            listOf(
                "https://example.com/post",
                "Quote",
                "<blockquote>Quote</blockquote>",
            ),
            Mf2TextExtractor.extractText(obj),
        )
    }

    @Test
    fun bailsOnUnregisteredEmbeddedType() {
        val card = Mf2Value.Object(
            Mf2Object(
                type = listOf("h-card"),
                properties = mutableMapOf(
                    "name" to listOf(Mf2Value.String("Jane")),
                    "url" to listOf(Mf2Value.String("https://example.com/jane")),
                ),
                children = null,
            )
        )

        val obj = entry(mutableMapOf("like-of" to listOf(card)))

        assertEquals(emptyList<String>(), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun doesNotDescendIntoNonTextNestedProperties() {
        val author = Mf2Value.Object(
            Mf2Object(
                type = listOf("h-card"),
                properties = mutableMapOf(
                    "name" to listOf(Mf2Value.String("Jane")),
                    "url" to listOf(Mf2Value.String("https://example.com/jane")),
                ),
                children = null,
            )
        )
        val cite = Mf2Value.Object(
            Mf2Object(
                type = listOf("h-cite"),
                properties = mutableMapOf("url" to listOf(Mf2Value.String("https://example.com/post"))),
                children = null,
            )
        )

        val obj = entry(
            mutableMapOf(
                "author" to listOf(author),
                "like-of" to listOf(cite),
            )
        )

        assertEquals(listOf("https://example.com/post"), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun skipsNonTextValuesAndBlankStrings() {
        val obj = entry(
            mutableMapOf(
                "like-of" to listOf(Mf2Value.Number(42), Mf2Value.String("   ")),
                "content" to listOf(Mf2Value.Boolean(true)),
            )
        )

        assertEquals(emptyList<String>(), Mf2TextExtractor.extractText(obj))
    }

    @Test
    fun preservesDuplicates() {
        val obj = entry(
            mutableMapOf(
                "like-of" to listOf(Mf2Value.String("https://example.com/post")),
                "bookmark-of" to listOf(Mf2Value.String("https://example.com/post")),
            )
        )

        assertEquals(listOf("https://example.com/post", "https://example.com/post"), Mf2TextExtractor.extractText(obj))
    }
}
