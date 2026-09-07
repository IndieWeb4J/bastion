package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.micropub.data.domain.Token
import dev.jacobandersen.bastion.micropub.data.entity.TokenEntity
import dev.jacobandersen.bastion.micropub.data.repository.TokenRepository
import dev.jacobandersen.bastion.micropub.security.MicropubToken
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class TokenService(
    val tokenRepository: TokenRepository
) {
    fun checkToken(token: String): Token? {
        val ent = tokenRepository.findByToken(token)
        if (ent != null) {
            val token = ent.toDomain()
            return if (token.expiresAt.isAfter(Instant.now())) {
                token
            } else {
                tokenRepository.delete(ent)
                null
            }
        }

        return null
    }

    fun rememberToken(rawToken: String, token: MicropubToken) {
        tokenRepository.save(
            TokenEntity(
                token = rawToken,
                decoded = token,
                expiresAt = Instant.now().plus(Duration.ofDays(7))
            )
        )
    }

    fun forgetToken(token: String) {
        val entity = tokenRepository.findByToken(token) ?: return
        tokenRepository.delete(entity)
    }
}