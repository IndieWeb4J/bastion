package dev.jacobandersen.bastion.indieauth.data.domain

import java.time.Instant

/**
 * A resolved, unexpired access token as seen by the rest of Bastion. Micropub
 * maps this onto its own token type; the IndieAuth module itself stays scope
 * agnostic (scopes are opaque strings).
 */
data class IssuedAccessToken(
    val me: String,
    val clientId: String,
    val scope: List<String>,
    val expiresAt: Instant,
)
