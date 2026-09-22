package dev.jacobandersen.bastion.graphql

import graphql.schema.idl.RuntimeWiring
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

@Configuration
class Mf2GraphqlWiringConfig : RuntimeWiringConfigurer {
    override fun configure(builder: RuntimeWiring.Builder) {
        builder.type("Mf2Value") { wiring ->
            wiring.typeResolver { env ->
                val obj = env.getObject<Any>()
                val typeName =
                    when (obj) {
                        is Mf2String -> "Mf2String"
                        is Mf2Boolean -> "Mf2Boolean"
                        is Mf2Number -> "Mf2Number"
                        is Mf2ObjectGraphql -> "Mf2Object"
                        is Mf2JsonObject -> "Mf2JsonObject"
                        is Mf2JsonArray -> "Mf2JsonArray"
                        else -> error("Unknown Mf2Value type: ${obj::class.simpleName}")
                    }
                env.schema.getObjectType(typeName)
            }
        }
        builder.type("Mf2JsonValue") { wiring ->
            wiring.typeResolver { env ->
                val obj = env.getObject<Any>()
                val typeName =
                    when (obj) {
                        is Mf2JsonString -> "Mf2JsonString"
                        is Mf2JsonNumber -> "Mf2JsonNumber"
                        is Mf2JsonBoolean -> "Mf2JsonBoolean"
                        is Mf2JsonObject -> "Mf2JsonObject"
                        is Mf2JsonArray -> "Mf2JsonArray"
                        else -> error("Unknown Mf2JsonValue type: ${obj::class.simpleName}")
                    }
                env.schema.getObjectType(typeName)
            }
        }
    }
}
