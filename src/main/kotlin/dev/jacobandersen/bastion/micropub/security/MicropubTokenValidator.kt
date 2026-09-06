package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.data.service.TokenService
import dev.jacobandersen.bastion.micropub.security.auth.IndieAuthService
import dev.jacobandersen.bastion.util.StringUtil.excerpt
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException

private val logger = KotlinLogging.logger {}

@Component
class MicropubTokenValidator(
    private val tokenService: TokenService,
    private val service: IndieAuthService
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val existingToken = tokenService.checkToken(rawToken)
        if (existingToken != null) {
            logger.info { "Previously seen token is still valid, using it" }
            return MicropubAuthentication(
                rawToken,
                existingToken.decoded,
                true
            )
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

        logger.info { "Micropub token validated: $token" }
        tokenService.rememberToken(rawToken, token)

        return MicropubAuthentication(rawToken, token, true)
    }

    private fun describeBody(e: RestClientResponseException): String {
        val body = e.getResponseBodyAsString()?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) ": $body" else ""
    }

    companion object {
        const val MAX_ERROR_BODY_LENGTH = 2000
    }
}