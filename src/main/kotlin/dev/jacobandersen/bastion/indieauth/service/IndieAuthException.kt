package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.type.IndieAuthError

/**
 * A protocol error raised by the IndieAuth services, carrying the OAuth error
 * code and a human-readable description. Controllers translate it into the
 * appropriate response shape (a redirect on the authorization endpoints, JSON
 * on the token endpoint).
 */
class IndieAuthException(
    val code: IndieAuthError.Code,
    description: String? = null,
) : RuntimeException(description) {
    fun toError(): IndieAuthError = IndieAuthError.of(code, message)
}
