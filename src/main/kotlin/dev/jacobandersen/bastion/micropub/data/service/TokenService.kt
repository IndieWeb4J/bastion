package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.micropub.data.domain.Token
import dev.jacobandersen.bastion.micropub.data.entity.TokenEntity
import dev.jacobandersen.bastion.micropub.data.repository.TokenRepository
import dev.jacobandersen.bastion.micropub.security.MicropubToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class TokenService(
    private val tokenRepository: TokenRepository,
) {
    @Transactional
    fun checkToken(rawToken: String): Token? {
        val entity = tokenRepository.findByToken(rawToken) ?: return null
        val cached = entity.toDomain()
        if (cached.expiresAt.isAfter(Instant.now())) {
            return cached
        }
        tokenRepository.delete(entity)
        return null
    }

    @Transactional
    fun rememberToken(
        rawToken: String,
        token: MicropubToken,
    ) {
        tokenRepository.save(
            TokenEntity(
                token = rawToken,
                decoded = token,
                expiresAt = Instant.now().plus(REMEMBER_DURATION),
            ),
        )
    }

    @Transactional
    fun forgetToken(rawToken: String) {
        val entity = tokenRepository.findByToken(rawToken) ?: return
        tokenRepository.delete(entity)
    }

    companion object {
        private val REMEMBER_DURATION: Duration = Duration.ofDays(7)
    }
}
