package dev.jacobandersen.bastion.indieauth.data.domain

import java.time.Instant

/**
 * The result of exchanging an authorization code: the raw access token (secret,
 * returned to the client exactly once) plus the identity and scope it carries.
 * An empty [scope] string means the login-only (empty-scope) grant.
 */
data class IssuedToken(
    val accessToken: String,
    val scope: String,
    val me: String,
    val expiresAt: Instant,
)
