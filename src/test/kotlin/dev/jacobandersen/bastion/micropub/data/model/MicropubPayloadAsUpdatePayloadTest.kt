package dev.jacobandersen.bastion.micropub.data.model

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class MicropubPayloadAsUpdatePayloadTest {
    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun parse(json: String): MicropubUpdatePayload {
        val root = mapper.readTree(json) as ObjectNode
        return MicropubPayload.Json(root).asUpdatePayload()
    }

    @Test
    fun parsesUrl() {
        val payload = parse("""{"url": "https://example.com/posts/1"}""")

        assertEquals("https://example.com/posts/1", payload.url)
        assertNull(payload.replacements)
        assertNull(payload.additions)
        assertNull(payload.removals)
    }

    @Test
    fun throwsWhenUrlMissing() {
        assertThrows(IllegalArgumentException::class.java) { parse("""{}""") }
    }

    @Test
    fun throwsWhenUrlNotString() {
        assertThrows(IllegalArgumentException::class.java) { parse("""{"url": 42}""") }
    }

    @Test
    fun parsesReplacements() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "replace": {
                    "content": ["updated content"],
                    "category": ["tag-a", "tag-b"]
                }
            }
            """,
            )

        assertEquals(
            mapOf<String, List<Mf2Value>>(
                "content" to listOf(Mf2Value.String("updated content")),
                "category" to listOf(Mf2Value.String("tag-a"), Mf2Value.String("tag-b")),
            ),
            payload.replacements,
        )
    }

    @Test
    fun parsesAdditions() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "add": {
                    "category": ["new-tag"]
                }
            }
            """,
            )

        assertEquals(
            mapOf<String, List<Mf2Value>>("category" to listOf(Mf2Value.String("new-tag"))),
            payload.additions,
        )
    }

    @Test
    fun parsesNestedMf2Values() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "replace": {
                    "author": [
                        {
                            "type": ["h-card"],
                            "properties": {
                                "name": ["Jacob"]
                            }
                        }
                    ]
                }
            }
            """,
            )

        assertEquals(
            mapOf<String, List<Mf2Value>>(
                "author" to
                    listOf(
                        Mf2Value.Object(
                            Mf2Object(
                                type = listOf("h-card"),
                                properties =
                                    mapOf(
                                        "name" to listOf(Mf2Value.String("Jacob")),
                                    ),
                                children = null,
                            ),
                        ),
                    ),
            ),
            payload.replacements,
        )
    }

    @Test
    fun ignoresUnparseableValueElements() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "replace": {
                    "count": [1, 2.5, true, "three"]
                }
            }
            """,
            )

        assertEquals(
            mapOf<String, List<Mf2Value>>(
                "count" to listOf(Mf2Value.Number(1), Mf2Value.Double(2.5), Mf2Value.Boolean(true), Mf2Value.String("three")),
            ),
            payload.replacements,
        )
    }

    @Test
    fun parsesDeleteAll() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "delete": ["content", "category"]
            }
            """,
            )

        assertEquals(MicropubUpdatePayload.Removals.All(listOf("content", "category")), payload.removals)
    }

    @Test
    fun parsesDeleteMany() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "delete": {
                    "category": ["tag-a", "tag-b"],
                    "content": ["old content"]
                }
            }
            """,
            )

        assertEquals(
            MicropubUpdatePayload.Removals.Many(
                mapOf(
                    "category" to listOf(Mf2Value.String("tag-a"), Mf2Value.String("tag-b")),
                    "content" to listOf(Mf2Value.String("old content")),
                ),
            ),
            payload.removals,
        )
    }

    @Test
    fun parsesDeleteManyTypedValues() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "delete": {
                    "count": [1, 2.5, true, "three"]
                }
            }
            """,
            )

        assertEquals(
            MicropubUpdatePayload.Removals.Many(
                mapOf(
                    "count" to listOf(Mf2Value.Number(1), Mf2Value.Double(2.5), Mf2Value.Boolean(true), Mf2Value.String("three")),
                ),
            ),
            payload.removals,
        )
    }

    @Test
    fun ignoresMalformedSections() {
        val payload =
            parse(
                """
            {
                "url": "https://example.com/posts/1",
                "replace": "not an object",
                "add": [1, 2],
                "delete": "not an object or array"
            }
            """,
            )

        assertNull(payload.replacements)
        assertNull(payload.additions)
        assertNull(payload.removals)
    }

    @Test
    fun parsesEmptyReplaceObject() {
        val payload = parse("""{"url": "https://example.com/posts/1", "replace": {}}""")

        assertEquals(mapOf<String, List<Mf2Value>>(), payload.replacements)
    }

    @Test
    fun rejectsNonArrayReplaceValue() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"url": "https://example.com/posts/1", "replace": {"content": "not an array"}}""")
        }
    }

    @Test
    fun rejectsNonArrayDeleteObjectValue() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"url": "https://example.com/posts/1", "delete": {"category": "not an array"}}""")
        }
    }

    @Test
    fun rejectsNonStringDeleteEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"url": "https://example.com/posts/1", "delete": ["content", 5]}""")
        }
    }

    @Test
    fun formBodyThrows() {
        val payload = MicropubPayload.Form(mapOf("h" to arrayOf("entry")))

        assertThrows(IllegalArgumentException::class.java) { payload.asUpdatePayload() }
    }
}
