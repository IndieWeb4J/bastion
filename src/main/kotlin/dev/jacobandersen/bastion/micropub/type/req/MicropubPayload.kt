package dev.jacobandersen.bastion.micropub.type.req

import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import dev.jacobandersen.mf24j.json.toMf2Object
import dev.jacobandersen.mf24j.json.toMf2ValueOrNull
import dev.jacobandersen.bastion.micropub.util.toMf2Object
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

sealed interface MicropubPayload {
    data class Json(
        val value: ObjectNode,
    ) : MicropubPayload

    data class Form(
        val value: Map<String, Array<String>>,
    ) : MicropubPayload

    fun asMf2Object(): Mf2Object =
        when (this) {
            is Form -> this.value.toMf2Object()
            is Json -> this.value.toMf2Object()
        }

    fun getUrl(): String =
        when (this) {
            is Form -> {
                this.value["url"]?.firstOrNull()
                    ?: throw IllegalArgumentException("missing required url")
            }

            is Json -> {
                this.value.requiredString("url")
            }
        }

    fun asUpdatePayload(): MicropubUpdatePayload {
        if (this is Form) {
            throw IllegalArgumentException("update payload is not defined for form body")
        }

        val root = (this as Json).value
        return MicropubUpdatePayload(
            url = root.requiredString("url"),
            replacements = root.optionalMf2Values("replace"),
            additions = root.optionalMf2Values("add"),
            removals = root.optionalDeletions("delete"),
        )
    }
}

private fun ObjectNode.requiredString(key: String): String {
    val node = this[key]
    if (node == null || !node.isString) {
        throw IllegalArgumentException("$key is required and must be a string")
    }
    return node.asString()
}

private fun ObjectNode.optionalMf2Values(key: String): Map<String, List<Mf2Value>>? = (this[key] as? ObjectNode)?.asMf2ValuesMap()

private fun ObjectNode.asMf2ValuesMap(): Map<String, List<Mf2Value>> {
    val properties = mutableMapOf<String, List<Mf2Value>>()
    this.properties().forEach { (property, values) ->
        val array = values as? ArrayNode ?: throw IllegalArgumentException("Property '$property' must be an array")
        properties[property] = array.mapNotNull { it.toMf2ValueOrNull() }
    }
    return properties
}

private fun ObjectNode.optionalDeletions(key: String): MicropubUpdatePayload.Removals? {
    val node = this[key] ?: return null
    return when (node) {
        is ArrayNode -> {
            val propertyNames =
                buildList {
                    node.forEach { element ->
                        if (!element.isString) throw IllegalArgumentException("Delete entries must be strings")
                        add(element.asString())
                    }
                }
            MicropubUpdatePayload.Removals.All(propertyNames)
        }

        is ObjectNode -> {
            MicropubUpdatePayload.Removals.Many(node.asMf2ValuesMap())
        }

        else -> {
            null
        }
    }
}
