package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.data.service.TokenService
import dev.jacobandersen.bastion.micropub.security.auth.IndieAuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

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
        } catch (_: Exception) {
            logger.info { "Micropub token validator: modern validation failed, try legacy validation" }
            service.legacyValidation("Bearer $rawToken")
        }

        logger.info { "Micropub token validated: $token" }
        tokenService.rememberToken(rawToken, token)

        return MicropubAuthentication(rawToken, token, true)
    }
}