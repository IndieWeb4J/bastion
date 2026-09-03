package dev.jacobandersen.bastion.micropub.type.mf2

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer

class Mf2ValueSerializer : StdSerializer<Mf2Value>(Mf2Value::class.java) {
    override fun serialize(value: Mf2Value, gen: JsonGenerator, provider: SerializationContext) {
        when (value) {
            is Mf2Value.String -> gen.writeString(value.value)
            is Mf2Value.Boolean -> gen.writeBoolean(value.value)
            is Mf2Value.Number -> gen.writeNumber(value.value)
            is Mf2Value.Float -> gen.writeNumber(value.value)
            is Mf2Value.Object -> provider.findValueSerializer(Mf2Object::class.java).serialize(value.value, gen, provider)
            is Mf2Value.Json -> provider.findValueSerializer(JsonNode::class.java).serialize(value.value, gen, provider)
        }
    }
}