package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.JsonNodeFactory

class Mf2GraphqlTest {
    private fun contentValue(
        html: String,
        text: String,
    ): Mf2Value {
        val node = JsonNodeFactory.instance.objectNode()
        node.put("html", html)
        node.put("value", text)
        return Mf2Value.Json(node)
    }

    @Test
    fun `firstText reads plain strings and json value objects`() {
        val obj =
            entry(
                mutableMapOf(
                    "name" to listOf(Mf2Value.String("Hello")),
                    "content" to listOf(contentValue("<p>Hi</p>", "Hi")),
                ),
            )

        assertEquals("Hello", Mf2Graphql.firstText(obj, "name"))
        assertEquals("Hi", Mf2Graphql.firstText(obj, "content"))
    }

    @Test
    fun `firstHtml reads the html of a json content value`() {
        val obj = entry(mutableMapOf("content" to listOf(contentValue("<p>Hi</p>", "Hi"))))
        assertEquals("<p>Hi</p>", Mf2Graphql.firstHtml(obj, "content"))
    }

    @Test
    fun `strings collects all string values`() {
        val obj = entry(mutableMapOf("category" to listOf(Mf2Value.String("a"), Mf2Value.String("b"))))
        assertEquals(listOf("a", "b"), Mf2Graphql.strings(obj, "category"))
    }

    @Test
    fun `normalizeProperties converts values to plain structures`() {
        val cite =
            Mf2Object(
                type = listOf("h-cite"),
                properties =
                    mutableMapOf(
                        "url" to listOf(Mf2Value.String("https://example.com/a")),
                        "name" to listOf(Mf2Value.String("A post")),
                    ),
                children = null,
            )
        val obj =
            entry(
                mutableMapOf(
                    "content" to listOf(contentValue("<p>Hi</p>", "Hi")),
                    "like-of" to listOf(Mf2Value.Object(cite)),
                ),
            )

        val normalized = Mf2Graphql.normalizeProperties(obj)
        val content = normalized.getValue("content").single() as Map<*, *>
        assertEquals("Hi", content["value"])
        val likeOf = normalized.getValue("like-of").single() as Map<*, *>
        assertEquals(listOf("h-cite"), likeOf["type"])
        val props = likeOf["properties"] as Map<*, *>
        assertEquals(listOf("https://example.com/a"), props["url"])
    }

    @Test
    fun `normalizeValue converts json nodes recursively`() {
        val node = JsonNodeFactory.instance.objectNode()
        node.put("value", "x")
        node.set(
            "nested",
            JsonNodeFactory.instance
                .arrayNode()
                .add(1)
                .add(true),
        )
        val result = Mf2Graphql.normalizeValue(Mf2Value.Json(node)) as Map<*, *>
        assertEquals("x", result["value"])
        assertEquals(listOf(1L, true), result["nested"])
        assertTrue(result.containsKey("nested"))
    }

    private fun entry(properties: MutableMap<String, List<Mf2Value>>): Mf2Object = Mf2Object(listOf("h-entry"), properties, null)
}
