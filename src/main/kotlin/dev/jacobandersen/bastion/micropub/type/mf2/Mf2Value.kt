package dev.jacobandersen.bastion.micropub.type.mf2

import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize

@JsonSerialize(using = Mf2ValueSerializer::class)
@JsonDeserialize(using = Mf2ValueDeserializer::class)
sealed interface Mf2Value {
    data class String(val value: kotlin.String): Mf2Value {
        override fun toString(): kotlin.String {
            return value
        }
    }

    data class Boolean(val value: kotlin.Boolean): Mf2Value {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    data class Number(val value: Long): Mf2Value {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    data class Float(val value: Double): Mf2Value {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    data class Object(val value: Mf2Object): Mf2Value {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }

    data class Json(val value: JsonNode): Mf2Value {
        override fun toString(): kotlin.String {
            return value.toString()
        }
    }
}

fun Any.isMf2Primitive(): Boolean {
    return when (this) {
        is String, Boolean, Byte, Short, Int, Long, Float, Double -> true
        else -> false
    }
}

fun Any.asMf2Value(): Mf2Value {
    return when (this) {
        is String -> Mf2Value.String(this)
        is Boolean -> Mf2Value.Boolean(this)
        is Byte, Short, Int, Long -> Mf2Value.Number(this as Long)
        is Float, Double -> Mf2Value.Float(this as Double)
        else -> Mf2Value.Object(this.asMf2Object())
    }
}
