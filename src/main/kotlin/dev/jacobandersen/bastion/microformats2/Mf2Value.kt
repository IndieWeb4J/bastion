package dev.jacobandersen.bastion.microformats2

import dev.jacobandersen.bastion.microformats2.json.Mf2ValueDeserializer
import dev.jacobandersen.bastion.microformats2.json.Mf2ValueSerializer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

/**
 * A single value of an mf2 property. Mirrors the JSON value forms defined by
 * the microformats2 parsing specification: plain strings/numbers/booleans,
 * nested microformat objects, and html objects (`{html, value}`).
 */
@JsonSerialize(using = Mf2ValueSerializer::class)
@JsonDeserialize(using = Mf2ValueDeserializer::class)
sealed interface Mf2Value {
    data class String(
        val value: kotlin.String,
    ) : Mf2Value {
        override fun toString(): kotlin.String = value
    }

    data class Boolean(
        val value: kotlin.Boolean,
    ) : Mf2Value {
        override fun toString(): kotlin.String = value.toString()
    }

    data class Number(
        val value: Long,
    ) : Mf2Value {
        override fun toString(): kotlin.String = value.toString()
    }

    data class Double(
        val value: kotlin.Double,
    ) : Mf2Value {
        override fun toString(): kotlin.String = value.toString()
    }

    data class Object(
        val value: Mf2Object,
    ) : Mf2Value {
        override fun toString(): kotlin.String = value.toString()
    }

    /** An html value object (`{html, value}`) or other JSON-structured value. */
    data class Json(
        val value: JsonNode,
    ) : Mf2Value {
        override fun toString(): kotlin.String = value.toString()
    }
}

/**
 * The plain-text value of this property value per the microformats2 parsing
 * specification: a string value is itself, an html value object contributes
 * its `value` key, and anything else has no plain-text representation.
 */
val Mf2Value.plainTextOrNull: String?
    get() =
        when (this) {
            is Mf2Value.String -> value
            is Mf2Value.Json -> value.get("value")?.takeIf(JsonNode::isString)?.asString()
            else -> null
        }

/** The `html` key of an html value object, or null for other value forms. */
val Mf2Value.htmlOrNull: String?
    get() =
        when (this) {
            is Mf2Value.Json -> value.get("html")?.takeIf(JsonNode::isString)?.asString()
            else -> null
        }
