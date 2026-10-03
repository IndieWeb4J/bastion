package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.type.UserProfile
import org.springframework.stereotype.Component

/**
 * Builds the informational `profile` object (5.3.4) from the static
 * single-user profile claims when the granted scopes include `profile`
 * and/or `email`. Returns null when neither scope was granted or no claims
 * are configured. The `email` address is only shared when both `profile` and
 * `email` scopes were granted.
 */
@Component
class ProfileClaimService(
    private val config: IndieAuthConfig,
) {
    fun forScopes(scopes: List<String>): UserProfile? {
        val lower = scopes.map { it.lowercase() }.toSet()
        if ("profile" !in lower) return null
        val configured = config.profile
        val profile =
            UserProfile(
                name = configured.name.takeIf { it.isNotBlank() },
                url = configured.url.takeIf { it.isNotBlank() },
                photo = configured.photo.takeIf { it.isNotBlank() },
                email = configured.email.takeIf { it.isNotBlank() && "email" in lower },
            )
        if (profile.name == null && profile.url == null && profile.photo == null && profile.email == null) return null
        return profile
    }
}
