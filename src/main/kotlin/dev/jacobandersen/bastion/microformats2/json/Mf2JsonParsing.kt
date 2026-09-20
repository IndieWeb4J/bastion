package dev.jacobandersen.bastion.microformats2.json

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * Reads an [Mf2Object] out of its canonical mf2 JSON representation:
 * `{"type": [...], "properties": {...}, "children": [...]}`.
 *
 * Missing or malformed entries are tolerated: a missing `type` defaults to
 * `h-entry`, non-array properties are skipped and unreadable values dropped.
 */
fun ObjectNode.toMf2Object(): Mf2Object {
    val type =
        (this["type"] as? ArrayNode)
            ?.mapNotNull { if (it.isString) it.asString() else null }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("h-entry")

    val children =
        (this["children"] as? ArrayNode)
            ?.mapNotNull { element -> (element as? ObjectNode)?.toMf2Object() }

    return Mf2Object(
        type = type,
        properties = parseProperties(this["properties"]),
        children = children,
    )
}

fun JsonNode.toMf2ValueOrNull(): Mf2Value? =
    when {
        isString -> {
            Mf2Value.String(asString())
        }

        isBoolean -> {
            Mf2Value.Boolean(booleanValue())
        }

        isIntegralNumber -> {
            Mf2Value.Number(longValue())
        }

        isFloatingPointNumber -> {
            Mf2Value.Double(doubleValue())
        }

        isObject -> {
            try {
                val objectNode = this as ObjectNode
                if (isMf2Object()) Mf2Value.Object(objectNode.toMf2Object()) else Mf2Value.Json(this)
            } catch (_: Exception) {
                null
            }
        }

        else -> {
            null
        }
    }

/** Whether the JSON node is a nested microformat object rather than a plain object value. */
fun JsonNode.isMf2Object(): Boolean =
    (this["type"] as? ArrayNode)?.takeIf { it.size() > 0 } != null && this["properties"] is ObjectNode

private fun parseProperties(node: JsonNode?): Map<String, List<Mf2Value>> {
    val objectNode = node as? ObjectNode ?: return emptyMap()
    val properties = linkedMapOf<String, List<Mf2Value>>()
    objectNode.properties().forEach { (key, value) ->
        val arrayNode = value as? ArrayNode ?: return@forEach
        properties[key] = arrayNode.mapNotNull { it.toMf2ValueOrNull() }
    }
    return properties
}
