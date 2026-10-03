package dev.jacobandersen.bastion.indieauth.type

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * The user's profile information returned alongside `me` when the `profile`
 * and/or `email` scopes were granted. Informational only: clients must not
 * treat it as canonical or make authentication decisions from it (5.3.4).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class UserProfile(
    val name: String? = null,
    val url: String? = null,
    val photo: String? = null,
    val email: String? = null,
)
