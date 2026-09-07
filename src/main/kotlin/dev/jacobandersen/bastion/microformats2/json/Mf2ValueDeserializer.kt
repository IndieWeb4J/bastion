package dev.jacobandersen.bastion.microformats2.json

import dev.jacobandersen.bastion.microformats2.Mf2Value
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.node.ObjectNode

/** Deserializes an [Mf2Value] from its canonical mf2 JSON value form. */
class Mf2ValueDeserializer : StdDeserializer<Mf2Value>(Mf2Value::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): Mf2Value =
        when (p.currentToken()) {
            JsonToken.VALUE_STRING -> {
                Mf2Value.String(p.valueAsString)
            }

            JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE -> {
                Mf2Value.Boolean(p.booleanValue)
            }

            JsonToken.VALUE_NUMBER_INT -> {
                Mf2Value.Number(p.longValue)
            }

            JsonToken.VALUE_NUMBER_FLOAT -> {
                Mf2Value.Double(p.doubleValue)
            }

            JsonToken.START_OBJECT -> {
                val node = ctxt.readTree(p)
                if (node.isMf2Object()) {
                    Mf2Value.Object((node as ObjectNode).toMf2Object())
                } else {
                    Mf2Value.Json(node)
                }
            }

            else -> {
                ctxt.handleUnexpectedToken(Mf2Value::class.java, p) as Mf2Value
            }
        }
}
