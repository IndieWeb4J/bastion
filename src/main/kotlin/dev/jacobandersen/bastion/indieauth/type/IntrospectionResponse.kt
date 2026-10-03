package dev.jacobandersen.bastion.indieauth.type

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * Token introspection response (IndieAuth 6.2, RFC 7662 plus `me`). Active
 * tokens report their identity, client, scope and timestamps; inactive tokens
 * report only `active: false` with no further detail.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class IntrospectionResponse(
    val active: Boolean,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val me: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val clientId: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scope: String? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val exp: Long? = null,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val iat: Long? = null,
)
