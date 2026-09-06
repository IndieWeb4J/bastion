package dev.jacobandersen.bastion.graphql

import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import graphql.schema.idl.RuntimeWiring
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

/**
 * A pass-through JSON scalar. Resolver values are normalized to plain
 * maps/lists/primitives (see [Mf2Graphql]) so the transport can serialize them
 * directly; the scalar does not attempt its own JSON encoding.
 */
@Configuration
class JsonScalarConfig : RuntimeWiringConfigurer {

    override fun configure(builder: RuntimeWiring.Builder) {
        builder.scalar(jsonScalar())
    }

    private fun jsonScalar(): GraphQLScalarType {
        return GraphQLScalarType.newScalar()
            .name("JSON")
            .description("An arbitrary JSON value")
            .coercing(JsonCoercing)
            .build()
    }

    private object JsonCoercing : Coercing<Any, Any> {
        override fun serialize(dataFetcherResult: Any): Any = dataFetcherResult

        override fun parseValue(input: Any): Any = input

        override fun parseLiteral(input: Any): Any = input
    }
}
