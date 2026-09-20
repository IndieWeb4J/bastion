package dev.jacobandersen.bastion.indieauth.security

import java.security.MessageDigest
import java.util.*

/**
 * PKCE (RFC 7636) helpers for the authorization-code flow. Bastion supports only
 * the `S256` challenge method; the `plain` method is refused because it offers
 * no protection against a leaked authorization code.
 */
object Pkce {
    /** The only challenge method Bastion accepts. */
    const val METHOD_S256 = "S256"

    /** Computes the S256 challenge for a verifier (used by tests and validation). */
    fun s256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /**
     * Verifies a code verifier against a stored challenge using constant-time
     * comparison. Returns false for malformed input rather than throwing.
     */
    fun verify(
        challenge: String,
        verifier: String,
    ): Boolean = Tokens.constantTimeEquals(challenge, s256(verifier))
}
