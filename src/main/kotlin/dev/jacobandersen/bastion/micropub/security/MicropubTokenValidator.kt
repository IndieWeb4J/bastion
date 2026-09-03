package dev.jacobandersen.bastion.micropub.security

import dev.jacobandersen.bastion.micropub.security.auth.IndieAuthService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class MicropubTokenValidator(
    private val service: IndieAuthService
) {
    fun validateToken(rawToken: String): MicropubAuthentication {
        val token = try {
            logger.info { "Micropub token validator: try modern validation" }
            service.modernValidation(rawToken)
        } catch (_: Exception) {
            logger.info { "Micropub token validator: modern validation failed, try legacy validation" }
            service.legacyValidation("Bearer $rawToken")
        }

        logger.info { "Micropub token validated: $token" }

        return MicropubAuthentication(rawToken, token, true)
    }
}