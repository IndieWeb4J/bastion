package dev.jacobandersen.bastion.micropub.security

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.deser.std.StdDeserializer

@JsonDeserialize(using = MicropubTokenDeserializer::class)
data class MicropubToken(
    val me: String,
    val clientId: String,
    val scope: List<MicropubTokenScope>
)

class MicropubTokenDeserializer : StdDeserializer<MicropubToken>(MicropubToken::class.java) {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): MicropubToken {
        val node = ctxt.readTree(p)
        val me = node["me"].asString()
        val clientId = node["client_id"].asString()
        val scope = node["scope"].asString().split(" ").map { MicropubTokenScope.fromString(it) }
        return MicropubToken(me, clientId, scope)
    }
}
