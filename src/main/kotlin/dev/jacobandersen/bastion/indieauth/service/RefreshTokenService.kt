package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.domain.IssuedToken
import dev.jacobandersen.bastion.indieauth.data.entity.RefreshTokenEntity
import dev.jacobandersen.bastion.indieauth.data.repository.RefreshTokenRepository
import dev.jacobandersen.bastion.indieauth.security.Tokens
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.Scopes
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/** An access token plus its rotated replacement refresh token. */
data class RefreshedTokens(
    val accessToken: IssuedToken,
    val refreshToken: String,
    val scope: String,
)

/**
 * Issues and rotates refresh tokens (IndieAuth 5.5). Refresh tokens are only
 * issued for scoped grants, are single-use with rotation, and may narrow the
 * scope on each use. Clients that do not support refresh are unaffected: they
 * ignore the field and re-run the authorization flow when the access token
 * expires.
 */
@Service
class RefreshTokenService(
    private val config: IndieAuthConfig,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val accessTokenService: AccessTokenService,
) {
    /** Issues a refresh token for a scoped grant. Returns null for empty scopes. */
    @Transactional
    fun issue(
        me: String,
        clientId: String,
        scope: String,
    ): String? {
        if (scope.isBlank()) return null
        val raw = Tokens.random()
        val now = Instant.now()
        refreshTokenRepository.save(
            RefreshTokenEntity(
                tokenHash = Tokens.sha256(raw),
                me = me,
                clientId = clientId,
                scope = scope,
                issuedAt = now,
                expiresAt = now.plus(config.refreshTokenTtl),
            ),
        )
        return raw
    }

    /**
     * Exchanges a refresh token for a new access token plus a replacement
     * refresh token. The requested scope must be a subset of the granted
     * scope; a blank request keeps the original scope.
     */
    @Transactional
    fun refresh(
        refreshToken: String,
        clientId: String,
        requestedScope: String?,
    ): RefreshedTokens {
        val tokenHash = Tokens.sha256(refreshToken)
        val stored =
            refreshTokenRepository.findByTokenHash(tokenHash)
                ?: throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The refresh token is invalid")

        if (stored.expiresAt.isBefore(Instant.now())) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The refresh token has expired")
        }
        if (stored.clientId != clientId) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The client_id does not match the refresh token")
        }

        val granted = Scopes.parse(stored.scope)
        val effective =
            if (requestedScope.isNullOrBlank()) {
                granted
            } else {
                val asked = Scopes.parse(requestedScope)
                val excess = asked.filterNot { it in granted }
                if (excess.isNotEmpty()) {
                    throw IndieAuthException(
                        IndieAuthError.Code.INVALID_SCOPE,
                        "Refresh cannot widen scope: ${excess.joinToString(" ")}",
                    )
                }
                asked
            }

        val claimed = refreshTokenRepository.claim(tokenHash, Instant.now())
        if (claimed == 0) {
            logger.warn { "Refresh token already used (possible replay)" }
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The refresh token has already been used")
        }

        val scopeString = Scopes.join(effective)
        val access = accessTokenService.issue(stored.me, stored.clientId, scopeString)
        val replacement =
            issue(stored.me, stored.clientId, scopeString)
                ?: throw IllegalStateException("Refresh replacement requires a non-empty scope")

        logger.info { "Rotated refresh token for ${stored.me} (client ${stored.clientId})" }

        return RefreshedTokens(accessToken = access, refreshToken = replacement, scope = scopeString)
    }

    /** Revokes a refresh token by hash. Returns true when a row was removed. */
    @Transactional
    fun revoke(rawToken: String): Boolean = refreshTokenRepository.deleteByTokenHash(Tokens.sha256(rawToken)) > 0
}
