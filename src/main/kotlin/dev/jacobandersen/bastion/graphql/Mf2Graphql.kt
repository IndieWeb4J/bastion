package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.microformats2.firstHtml
import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.microformats2.texts
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * Reads common h-entry values out of an [Mf2Object] and normalizes mf2 values
 * into plain maps/lists/primitives so they serialize cleanly regardless of the
 * JSON library used by the GraphQL transport.
 */
internal object Mf2Graphql {
    fun firstText(
        obj: Mf2Object,
        key: String,
    ): String? = obj.firstText(key)

    fun firstHtml(
        obj: Mf2Object,
        key: String,
    ): String? = obj.firstHtml(key)

    fun strings(
        obj: Mf2Object,
        key: String,
    ): List<String> = obj.texts(key)

    fun normalizeProperties(obj: Mf2Object): Map<String, List<Any?>> =
        obj.properties.mapValues { (_, values) -> values.map(::normalizeValue) }

    fun normalizeObject(obj: Mf2Object): Map<String, Any?> {
        val out =
            linkedMapOf<String, Any?>(
                "type" to obj.type,
                "properties" to normalizeProperties(obj),
            )
        obj.children?.let { children ->
            if (children.isNotEmpty()) {
                out["children"] = children.map(::normalizeObject)
            }
        }
        return out
    }

    fun normalizeValue(value: Mf2Value): Any? =
        when (value) {
            is Mf2Value.String -> value.value
            is Mf2Value.Boolean -> value.value
            is Mf2Value.Number -> value.value
            is Mf2Value.Double -> value.value
            is Mf2Value.Object -> normalizeObject(value.value)
            is Mf2Value.Json -> nodeToPlain(value.value)
        }

    private fun nodeToPlain(node: JsonNode): Any? =
        when {
            node.isNull -> null
            node.isString -> node.asString()
            node.isBoolean -> node.asBoolean()
            node.isIntegralNumber -> node.asLong()
            node.isFloatingPointNumber -> node.asDouble()
            node is ArrayNode -> node.mapNotNull(::nodeToPlain)
            node is ObjectNode -> node.properties().associate { it.key to nodeToPlain(it.value) }
            else -> null
        }
}
