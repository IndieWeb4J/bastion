package dev.jacobandersen.bastion.indieauth.type

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * The IndieAuth profile URL response (5.3.2): returned from the authorization
 * endpoint when the client only needs to know who logged in. Never carries
 * an access token; clients needing one redeem at the token endpoint instead.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ProfileUrlResponse(
    val me: String,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val profile: UserProfile? = null,
)
