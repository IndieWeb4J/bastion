package dev.jacobandersen.bastion.micropub.security

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.ser.std.StdSerializer

@JsonSerialize(using = MicropubTokenSerializer::class)
@JsonDeserialize(using = MicropubTokenDeserializer::class)
data class MicropubToken(
    val me: String,
    val clientId: String,
    val scope: List<MicropubTokenScope>,
)

class MicropubTokenSerializer : StdSerializer<MicropubToken>(MicropubToken::class.java) {
    override fun serialize(
        value: MicropubToken,
        gen: JsonGenerator,
        ctxt: SerializationContext,
    ) {
        gen.writeStartObject()
        gen.writeName("me")
        gen.writeString(value.me)
        gen.writeName("client_id")
        gen.writeString(value.clientId)
        gen.writeName("scope")
        gen.writeString(value.scope.joinToString(" "))
        gen.writeEndObject()
    }
}

class MicropubTokenDeserializer : StdDeserializer<MicropubToken>(MicropubToken::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): MicropubToken {
        val node = ctxt.readTree(p)
        val me =
            node["me"]?.asString()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Token response is missing the 'me' property")
        val clientId =
            node["client_id"]?.asString()?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Token response is missing the 'client_id' property")
        val rawScope = node.get("scope")?.asString() ?: ""
        val scope =
            rawScope
                .split(' ')
                .filter { it.isNotBlank() }
                .mapNotNull { MicropubTokenScope.fromStringOrNull(it) }
        return MicropubToken(me, clientId, scope)
    }
}
