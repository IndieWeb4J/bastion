package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.sigil.SigilIntrospectionClient
import dev.jacobandersen.bastion.sigil.SigilProperties
import dev.jacobandersen.bastion.url.UrlNormalizer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Validates a Micropub bearer token against the Sigil IndieAuth service. Sigil
 * is the source of truth for issued access tokens, so validation is a remote
 * introspection call (cached briefly by [SigilIntrospectionClient]) rather than
 * a local lookup.
 */
@Component
class MicropubTokenValidator(
    private val sigilIntrospectionClient: SigilIntrospectionClient,
    private val sigilProperties: SigilProperties,
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val issued =
            sigilIntrospectionClient.introspect(rawToken)
                ?: throw IllegalArgumentException("Access token is not valid")

        if (!sameIdentity(sigilProperties.me, issued.me)) {
            logger.warn {
                "Token belongs to a different identity (expected ${sigilProperties.me}, got ${issued.me})"
            }
            throw IllegalArgumentException("Token is not for the expected identity")
        }

        val scopes =
            issued.scope
                .orEmpty()
                .split(' ')
                .mapNotNull { MicropubTokenScope.fromStringOrNull(it) }
        val token = MicropubToken(issued.me ?: sigilProperties.me, issued.clientId.orEmpty(), scopes)

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
