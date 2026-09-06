package dev.jacobandersen.bastion.microformats2

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import kotlin.collections.get
import kotlin.collections.iterator

data class Mf2Object(val type: List<String>, val properties: MutableMap<String, List<Mf2Value>>, val children: List<Mf2Object>?) {
    fun primaryType(): String {
        return type.firstOrNull() ?: throw IllegalStateException("Mf2Object has no type")
    }

    fun getProperty(key: String): List<Mf2Value> {
        return properties[key] ?: listOf()
    }

    fun getFirstProperty(key: String): Mf2Value? {
        return getProperty(key).firstOrNull()
    }

    operator fun get(key: String): List<Mf2Value> {
        return getProperty(key)
    }

    fun deleteProperty(key: String) {
        properties.remove(key)
    }

    fun setProperty(key: String, value: Mf2Value) {
        properties[key] = listOf(value)
    }

    operator fun set(key: String, value: Mf2Value) {
        setProperty(key, value)
    }

    fun setProperty(key: String, value: List<Mf2Value>) {
        properties[key] = value
    }

    operator fun set(key: String, value: List<Mf2Value>) {
        setProperty(key, value)
    }

    fun addProperty(key: String, value: Mf2Value) {
        addProperty(key, listOf(value))
    }

    fun addProperty(key: String, value: List<Mf2Value>) {
        properties.compute(key) { _, values ->
            return@compute values?.plus(value) ?: value
        }
    }

    fun hasProperty(key: String): Boolean {
        // Property might exist as empty array, while this method's
        // intention is to be used before getting something from the
        // property without further checks. Rather than only check if
        // key exists, we do that but also check if there is at least
        // one element.
        return (properties[key]?.size ?: 0) > 0
    }
}

fun Any.asMf2Object(): Mf2Object {
    return when (this) {
        is ObjectNode -> {
            val type = (this["type"] as? ArrayNode)
                ?.mapNotNull { if (it.isString) it.asString() else null }
                ?.takeIf { it.isNotEmpty() }
                ?.toList()
                ?: listOf("h-entry")
            val properties = parseJsonProperties(this["properties"])
            val children = (this["children"] as? ArrayNode)
                ?.mapNotNull { element -> try { element.asMf2Object() } catch (_: Exception) { null } }
            Mf2Object(type, properties, children)
        }

        is Map<*, *> -> {
            if (this.keys.all { it is String } && this.values.all { it is Array<*> || it is Map<*, *> }) {
                val h = (this["h"] as? Array<*>)?.filterIsInstance<String>()?.firstOrNull() ?: "entry"
                val type = listOf("h-$h")
                val propertyEntries = this.filterKeys { key ->
                    key is String && key != "h"
                }
                val properties = parseFormProperties(propertyEntries) ?: mutableMapOf()
                Mf2Object(type, properties, null)
            } else {
                throw IllegalArgumentException("Map given in $this cannot be read as Mf2Object")
            }
        }

        else -> throw IllegalArgumentException("Cannot convert $this to Mf2Object")
    }
}

private fun parseJsonProperties(obj: JsonNode?): MutableMap<String, List<Mf2Value>> {
    val objectNode = obj as? ObjectNode ?: return mutableMapOf()
    val safeMap = mutableMapOf<String, List<Mf2Value>>()
    objectNode.properties().forEach { (key, node) ->
        val arrayNode = node as? ArrayNode ?: return@forEach
        safeMap[key] = arrayNode.mapNotNull { element -> element.asMf2ValueOrNull() }
    }
    return safeMap
}

internal fun JsonNode.asMf2ValueOrNull(): Mf2Value? {
    return when {
        isString -> Mf2Value.String(asString())
        isBoolean -> Mf2Value.Boolean(booleanValue())
        isIntegralNumber -> Mf2Value.Number(longValue())
        isFloatingPointNumber -> Mf2Value.Float(doubleValue())
        isObject -> try {
            if (this.isMf2ObjectValue()) {
                Mf2Value.Object(this.asMf2Object())
            } else {
                Mf2Value.Json(this)
            }
        } catch (_: Exception) { null }
        else -> null
    }
}

internal fun JsonNode.isMf2ObjectValue(): Boolean {
    return (this["type"] as? ArrayNode)?.takeIf { it.size() > 0 } != null && this["properties"] is ObjectNode
}

private fun parseFormProperties(obj: Any): MutableMap<String, List<Mf2Value>>? {
    val rawMap = obj as? Map<*, *> ?: return null
    val safeMap = mutableMapOf<String, List<Mf2Value>>()

    for ((key, value) in rawMap) {
        val safeKey = key as? String ?: continue
        val rawArray = value as? Array<*> ?: continue

        val validatedElements = mutableListOf<Mf2Value>()

        for (item in rawArray) {
            when {
                item == null -> { }

                item.isMf2Primitive() -> validatedElements.add(item.asMf2Value())

                item is Map<*, *> -> {
                    try {
                        validatedElements.add(Mf2Value.Object(item.asMf2Object()))
                    } catch (_: Exception) { }
                }

                else -> { }
            }
        }

        safeMap[safeKey] = validatedElements.toList()
    }

    return safeMap
}