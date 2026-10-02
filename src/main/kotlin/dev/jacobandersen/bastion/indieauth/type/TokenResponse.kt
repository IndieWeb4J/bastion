package dev.jacobandersen.bastion.indieauth.type

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * The successful response from the IndieAuth token endpoint. `scope` is the
 * space-delimited set of granted scopes and `me` the identity the token was
 * issued for. `scope` is omitted for login-only (empty-scope) grants.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val scope: String?,
    val me: String,
)
