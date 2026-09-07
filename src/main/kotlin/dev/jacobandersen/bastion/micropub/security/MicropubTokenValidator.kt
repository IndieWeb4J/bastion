package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import dev.jacobandersen.bastion.url.UrlNormalizer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Validates a Micropub bearer token against the access tokens Bastion itself
 * issued (via the IndieAuth token endpoint). Bastion is now its own IndieAuth
 * provider, so validation is a local, hash-based lookup of issued tokens rather
 * than a call to an external token endpoint.
 */
@Component
class MicropubTokenValidator(
    private val accessTokenService: AccessTokenService,
    @Value("\${bastion.indieauth.me}") private val expectedMe: String,
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val issued =
            accessTokenService.resolve(rawToken)
                ?: throw IllegalArgumentException("Access token is not valid")

        if (!sameIdentity(expectedMe, issued.me)) {
            logger.warn { "Token belongs to a different identity (expected $expectedMe, got ${issued.me})" }
            throw IllegalArgumentException("Token is not for the expected identity")
        }

        val scopes = issued.scope.mapNotNull { MicropubTokenScope.fromStringOrNull(it) }
        val token = MicropubToken(issued.me, issued.clientId, scopes)

        return MicropubAuthentication(rawToken, token, true)
    }

    /**
     * Compare two profile URLs after normalization: lowercased scheme and
     * host, default ports dropped, trailing slash removed, fragment dropped.
     * The query string is not part of the identity.
     */
    private fun sameIdentity(
        expected: String,
        actual: String,
    ): Boolean {
        val normalizedExpected = UrlNormalizer.identity(expected) ?: return false
        val normalizedActual = UrlNormalizer.identity(actual) ?: return false
        return normalizedExpected == normalizedActual
    }
}
