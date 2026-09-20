package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.indieauth.data.domain.IssuedAccessToken
import dev.jacobandersen.bastion.indieauth.data.domain.IssuedToken
import dev.jacobandersen.bastion.indieauth.data.entity.AccessTokenEntity
import dev.jacobandersen.bastion.indieauth.data.entity.AuthorizationCodeEntity
import dev.jacobandersen.bastion.indieauth.data.repository.AccessTokenRepository
import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import dev.jacobandersen.bastion.indieauth.security.Pkce
import dev.jacobandersen.bastion.indieauth.security.Tokens
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Issues and resolves access tokens for the IndieAuth token endpoint and for
 * Micropub token validation. Tokens are stored as a SHA-256 hash only, so the
 * raw value cannot be recovered from the database.
 */
@Service
class AccessTokenService(
    private val config: IndieAuthConfig,
    private val accessTokenRepository: AccessTokenRepository,
    private val authorizationCodeRepository: AuthorizationCodeRepository,
) {
    /**
     * Exchanges an authorization code for an access token, enforcing the
     * single-use, expiry, `client_id`, `redirect_uri` and PKCE guarantees.
     */
    @Transactional
    fun exchange(
        code: String,
        clientId: String,
        redirectUri: String,
        codeVerifier: String?,
    ): IssuedToken {
        val codeHash = Tokens.sha256(code)
        val authorizationCode =
            authorizationCodeRepository.findByCodeHash(codeHash)
                ?: throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code is invalid")

        if (authorizationCode.expiresAt.isBefore(Instant.now())) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code has expired")
        }

        validateClient(authorizationCode, clientId, redirectUri)
        validatePkce(authorizationCode, codeVerifier)

        val claimed = authorizationCodeRepository.claim(codeHash, Instant.now())
        if (claimed == 0) {
            logger.warn { "Authorization code already used (possible replay)" }
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code has already been used")
        }

        val rawToken = Tokens.random()
        val expiresAt = Instant.now().plus(config.accessTokenTtl)
        accessTokenRepository.save(
            AccessTokenEntity(
                tokenHash = Tokens.sha256(rawToken),
                me = authorizationCode.me,
                clientId = authorizationCode.clientId,
                scope = authorizationCode.scope,
                issuedAt = Instant.now(),
                expiresAt = expiresAt,
            ),
        )

        logger.info { "Issued access token for ${authorizationCode.me} (client ${authorizationCode.clientId})" }

        return IssuedToken(
            accessToken = rawToken,
            scope = authorizationCode.scope,
            me = authorizationCode.me,
            expiresAt = expiresAt,
        )
    }

    /**
     * Resolves a raw bearer token to its issued identity, or null when the
     * token is unknown or expired. Used by Micropub token validation.
     */
    @Transactional(readOnly = true)
    fun resolve(rawToken: String): IssuedAccessToken? {
        val entity = accessTokenRepository.findByTokenHash(Tokens.sha256(rawToken)) ?: return null
        if (entity.expiresAt.isBefore(Instant.now())) {
            return null
        }
        return entity.toDomain()
    }

    private fun validateClient(
        authorizationCode: AuthorizationCodeEntity,
        clientId: String,
        redirectUri: String,
    ) {
        if (authorizationCode.clientId != clientId) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The client_id does not match the issued code")
        }
        if (authorizationCode.redirectUri != redirectUri) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "The redirect_uri does not match the issued code"
            )
        }
    }

    private fun validatePkce(
        authorizationCode: AuthorizationCodeEntity,
        codeVerifier: String?,
    ) {
        val challenge = authorizationCode.codeChallenge ?: return
        if (codeVerifier == null || !Pkce.verify(challenge, codeVerifier)) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "The code_verifier does not match the code challenge"
            )
        }
    }
}
