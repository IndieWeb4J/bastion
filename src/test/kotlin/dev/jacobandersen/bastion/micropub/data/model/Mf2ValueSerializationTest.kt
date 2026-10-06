package dev.jacobandersen.bastion.micropub.data.model

import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class Mf2ValueSerializationTest {
    private val mapper =
        JsonMapper
            .builderWithJackson2Defaults()
            .addModule(KotlinModule.Builder().build())
            .build()

    @Test
    fun roundTripsAllValueTypes() {
        val obj =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mutableMapOf(
                        "name" to listOf(Mf2Value.String("hello world")),
                        "published" to listOf(Mf2Value.String("2026-08-31T00:00:00Z")),
                        "draft" to listOf(Mf2Value.Boolean(false)),
                        "count" to listOf(Mf2Value.Number(42)),
                        "rating" to listOf(Mf2Value.Double(4.5)),
                        "author" to
                            listOf(
                                Mf2Value.Object(
                                    Mf2Object(
                                        type = listOf("h-card"),
                                        properties =
                                            mutableMapOf(
                                                "name" to
                                                    listOf(
                                                        Mf2Value.String(
                                                            "Jacob",
                                                        ),
                                                    ),
                                            ),
                                        children = null,
                                    ),
                                ),
                            ),
                    ),
                children = null,
            )

        val json = mapper.writeValueAsString(obj)
        val roundTripped = mapper.readValue(json, Mf2Object::class.java)

        assertEquals(obj, roundTripped)
    }

    @Test
    fun roundTripsJsonValues() {
        val html = mapper.readTree("""{"html":"<b>hi</b>"}""")
        val photo = mapper.readTree("""{"value":"https://example.com/photo.jpg","alt":"a photo"}""")

        val obj =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mutableMapOf(
                        "content" to listOf(Mf2Value.Json(html)),
                        "photo" to listOf(Mf2Value.Json(photo)),
                    ),
                children = null,
            )

        val json = mapper.writeValueAsString(obj)
        val roundTripped = mapper.readValue(json, Mf2Object::class.java)

        assertEquals(obj, roundTripped)
    }

    @Test
    fun serializesToRawMf2Values() {
        val obj =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mutableMapOf(
                        "name" to listOf(Mf2Value.String("hello")),
                        "count" to listOf(Mf2Value.Number(7)),
                    ),
                children = null,
            )

        val json = mapper.writeValueAsString(obj)

        assertEquals("""{"type":["h-entry"],"properties":{"name":["hello"],"count":[7]},"children":null}""", json)
    }
}
