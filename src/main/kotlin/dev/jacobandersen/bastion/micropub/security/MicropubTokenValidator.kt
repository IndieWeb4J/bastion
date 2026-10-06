package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.url.UrlNormalizer
import dev.jacobandersen.sigil.client.SigilClientException
import dev.jacobandersen.sigil.client.TokenIntrospector
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Validates a Micropub bearer token against the Sigil IndieAuth service via the
 * `sigil-client` `TokenIntrospector`. Sigil is the source of truth for issued
 * access tokens, so validation is a remote introspection call (cached briefly
 * by the client) rather than a local lookup. The expected identity is a Bastion
 * policy, not the library's concern.
 */
@Component
class MicropubTokenValidator(
    private val tokenIntrospector: TokenIntrospector,
    @Value("\${bastion.sigil.me}") private val expectedMe: String,
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val issued =
            try {
                tokenIntrospector.introspect(rawToken)
            } catch (e: SigilClientException) {
                logger.warn(e) { "Sigil introspection failed; rejecting token" }
                throw IllegalArgumentException("Access token is not valid", e)
            }

        if (!issued.active) {
            throw IllegalArgumentException("Access token is not valid")
        }

        if (!sameIdentity(expectedMe, issued.me)) {
            logger.warn { "Token belongs to a different identity (expected $expectedMe, got ${issued.me})" }
            throw IllegalArgumentException("Token is not for the expected identity")
        }

        val scopes =
            issued.scope
                .orEmpty()
                .split(' ')
                .mapNotNull { MicropubTokenScope.fromStringOrNull(it) }
        val token = MicropubToken(issued.me ?: expectedMe, issued.clientId.orEmpty(), scopes)

        return MicropubAuthentication(rawToken, token, true)
    }

    /**
     * Compare two profile URLs after normalization: lowercased scheme and
     * host, default ports dropped, trailing slash removed, fragment dropped.
     * The query string is not part of the identity.
     */
    private fun sameIdentity(
        expected: String,
        actual: String?,
    ): Boolean {
        if (actual == null) return false
        val normalizedExpected = UrlNormalizer.identity(expected) ?: return false
        val normalizedActual = UrlNormalizer.identity(actual) ?: return false
        return normalizedExpected == normalizedActual
    }
}
