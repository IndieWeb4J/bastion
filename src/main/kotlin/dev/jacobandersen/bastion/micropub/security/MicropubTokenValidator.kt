package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.data.service.TokenService
import dev.jacobandersen.bastion.micropub.security.auth.IndieAuthService
import dev.jacobandersen.bastion.url.UrlNormalizer
import dev.jacobandersen.bastion.util.StringUtil.excerpt
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException

private val logger = KotlinLogging.logger {}

@Component
class MicropubTokenValidator(
    private val tokenService: TokenService,
    private val service: IndieAuthService,
    @Value("\${bastion.indieauth.me}") private val expectedMe: String,
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val existingToken = tokenService.checkToken(rawToken)
        if (existingToken != null) {
            if (sameIdentity(expectedMe, existingToken.decoded.me)) {
                logger.info { "Previously seen token is still valid, using it" }
                return MicropubAuthentication(
                    rawToken,
                    existingToken.decoded,
                    true
                )
            }
            logger.warn { "Cached token belongs to a different identity, evicting and re-validating" }
            tokenService.forgetToken(rawToken)
        }

        logger.info { "New token, validate it..."}

        val token = try {
            logger.info { "Micropub token validator: try modern validation" }
            service.modernValidation(rawToken)
        } catch (e: RestClientResponseException) {
            logger.info { "Micropub token validator: modern validation failed (HTTP ${e.statusCode.value()})${describeBody(e)}" }
            try {
                service.legacyValidation("Bearer $rawToken")
            } catch (e2: RestClientResponseException) {
                logger.warn { "Micropub token validator: legacy validation failed (HTTP ${e2.statusCode.value()})${describeBody(e2)}" }
                throw e2
            }
        } catch (_: Exception) {
            logger.info { "Micropub token validator: modern validation failed, try legacy validation" }
            service.legacyValidation("Bearer $rawToken")
        }

        if (!sameIdentity(expectedMe, token.me)) {
            logger.warn { "Token is not for the expected identity (expected $expectedMe, got ${token.me})" }
            throw IllegalArgumentException("Token is not for the expected identity")
        }

        logger.info { "Micropub token validated: $token" }
        tokenService.rememberToken(rawToken, token)

        return MicropubAuthentication(rawToken, token, true)
    }

    /**
     * Compare two profile URLs after normalization: lowercased scheme and
     * host, default ports dropped, trailing slash removed, fragment dropped.
     * The query string is not part of the identity.
     */
    private fun sameIdentity(expected: String, actual: String): Boolean {
        val normalizedExpected = UrlNormalizer.identity(expected) ?: return false
        val normalizedActual = UrlNormalizer.identity(actual) ?: return false
        return normalizedExpected == normalizedActual
    }

    private fun describeBody(e: RestClientResponseException): String {
        val body = e.getResponseBodyAsString()?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) ": $body" else ""
    }

    companion object {
        const val MAX_ERROR_BODY_LENGTH = 2000
    }
}