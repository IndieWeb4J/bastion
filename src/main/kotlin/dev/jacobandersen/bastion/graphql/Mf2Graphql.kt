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
 * Reads common h-entry values out of an [Mf2Object] and converts mf2 values
 * into typed GraphQL DTOs.
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

    fun toProperties(obj: Mf2Object): List<Mf2PropertyGraphql> = toProperties(obj, null)

    fun toProperties(
        obj: Mf2Object,
        allowedNames: Set<String>?,
    ): List<Mf2PropertyGraphql> =
        obj.properties
            .filter { allowedNames == null || it.key in allowedNames }
            .map { (name, values) ->
                Mf2PropertyGraphql(name, values.map(::toMf2Value))
            }

    fun toMf2ObjectGraphql(obj: Mf2Object): Mf2ObjectGraphql =
        Mf2ObjectGraphql(
            type = obj.type,
            properties = toProperties(obj),
            children = obj.children?.map(::toMf2ObjectGraphql).orEmpty(),
        )

    fun toMf2Value(value: Mf2Value): Mf2ValueGraphql =
        when (value) {
            is Mf2Value.String -> Mf2String(value.value)
            is Mf2Value.Boolean -> Mf2Boolean(value.value)
            is Mf2Value.Number -> Mf2Number(value.value.toDouble())
            is Mf2Value.Double -> Mf2Number(value.value)
            is Mf2Value.Object -> toMf2ObjectGraphql(value.value)
            is Mf2Value.Json -> toJsonValueGraphql(value.value) ?: Mf2JsonObject(emptyList())
        }

    private fun toJsonValueGraphql(node: JsonNode): Mf2ValueGraphql? =
        when {
            node.isNull -> null
            node.isString -> Mf2String(node.asString())
            node.isBoolean -> Mf2Boolean(node.asBoolean())
            node.isNumber -> Mf2Number(node.asDouble())
            node is ArrayNode -> toJsonArray(node)
            node is ObjectNode -> toJsonObject(node)
            else -> Mf2JsonObject(emptyList())
        }

    fun toJsonValue(node: JsonNode): Mf2JsonValueGraphql? =
        when {
            node.isNull -> null
            node.isString -> Mf2JsonString(node.asString())
            node.isBoolean -> Mf2JsonBoolean(node.asBoolean())
            node.isNumber -> Mf2JsonNumber(node.asDouble())
            node is ArrayNode -> toJsonArray(node)
            node is ObjectNode -> toJsonObject(node)
            else -> null
        }

    private fun toJsonObject(node: ObjectNode): Mf2JsonObject {
        val fields =
            node.properties().map { (k, v) ->
                Mf2JsonField(k, toJsonValue(v))
            }
        return Mf2JsonObject(fields)
    }

    private fun toJsonArray(node: ArrayNode): Mf2JsonArray {
        val values =
            node
                .elements()
                .asSequence()
                .map { toJsonValue(it) }
                .toList()
        return Mf2JsonArray(values)
    }
}
