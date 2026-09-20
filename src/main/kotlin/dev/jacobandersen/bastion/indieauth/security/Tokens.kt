package dev.jacobandersen.bastion.indieauth.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * Cryptographically secure token generation and hashing for the IndieAuth
 * `state`/`code`/`access_token` round trip. Raw secrets are handed to the caller
 * exactly once; Bastion persists only a SHA-256 digest of each, so a leaked
 * database cannot be replayed against the live endpoints.
 */
object Tokens {
    private val RANDOM = SecureRandom()

    /** A URL-safe base64url-encoded random token with at least [bits] bits of entropy. */
    fun random(bits: Int = DEFAULT_BITS): String {
        val bytes = ByteArray(bits / 8)
        RANDOM.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The SHA-256 digest of [value], hex-encoded, used as the at-rest storage key. */
    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    /** Constant-time comparison of two secrets, immune to timing side channels. */
    fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private const val DEFAULT_BITS = 256
}
