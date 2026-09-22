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
    fun `toProperties converts values to typed structures`() {
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

        val properties = Mf2Graphql.toProperties(obj).associateBy { it.name }
        val content = properties.getValue("content").values.single() as Mf2JsonObject
        val contentFields = content.fields.associate { it.name to it.value }
        assertEquals("Hi", (contentFields["value"] as Mf2JsonString).value)
        assertEquals("<p>Hi</p>", (contentFields["html"] as Mf2JsonString).value)

        val likeOf = properties.getValue("like-of").values.single() as Mf2ObjectGraphql
        assertEquals(listOf("h-cite"), likeOf.type)
        val likeProps = likeOf.properties.associateBy { it.name }
        assertEquals("https://example.com/a", (likeProps.getValue("url").values.single() as Mf2String).value)
    }

    @Test
    fun `toMf2Value handles number collapsing`() {
        val longVal = Mf2Value.Number(42L)
        val doubleVal = Mf2Value.Double(3.14)

        val longResult = Mf2Graphql.toMf2Value(longVal) as Mf2Number
        val doubleResult = Mf2Graphql.toMf2Value(doubleVal) as Mf2Number

        assertEquals(42.0, longResult.value)
        assertEquals(3.14, doubleResult.value)
    }

    @Test
    fun `toJsonValue converts json nodes recursively`() {
        val node = JsonNodeFactory.instance.objectNode()
        node.put("value", "x")
        node.set(
            "nested",
            JsonNodeFactory.instance
                .arrayNode()
                .add(1)
                .add(true),
        )
        val result = Mf2Graphql.toJsonValue(node) as Mf2JsonObject
        val fields = result.fields.associate { it.name to it.value }
        assertEquals("x", (fields["value"] as Mf2JsonString).value)
        val nested = fields["nested"] as Mf2JsonArray
        assertEquals(2, nested.values.size)
        assertEquals(1.0, (nested.values[0] as Mf2JsonNumber).value)
        assertEquals(true, (nested.values[1] as Mf2JsonBoolean).value)
        assertTrue(fields.containsKey("nested"))
    }

    @Test
    fun `toJsonValue handles null`() {
        val node = JsonNodeFactory.instance.objectNode()
        node.set("maybe", JsonNodeFactory.instance.nullNode())
        val result = Mf2Graphql.toJsonValue(node) as Mf2JsonObject
        val field = result.fields.single { it.name == "maybe" }
        assertEquals(null, field.value)
    }

    private fun entry(properties: MutableMap<String, List<Mf2Value>>): Mf2Object = Mf2Object(listOf("h-entry"), properties, null)
}
