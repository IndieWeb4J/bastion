package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.data.repository.AuthorizationCodeRepository
import dev.jacobandersen.bastion.indieauth.security.Pkce
import dev.jacobandersen.bastion.indieauth.security.Tokens
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.Scopes
import dev.jacobandersen.bastion.indieauth.type.UserProfile
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/** The result of redeeming a code at the authorization endpoint: identity plus granted scopes. */
data class IssuedProfile(
    val me: String,
    val scope: String,
    val profile: UserProfile?,
)

/**
 * Redeems an authorization code at the authorization endpoint (5.3.2),
 * returning only the canonical profile URL and optional profile information.
 * Never issues an access token; scoped clients redeem at the token endpoint.
 */
@Service
class ProfileUrlService(
    private val authorizationCodeRepository: AuthorizationCodeRepository,
    private val profileClaimService: ProfileClaimService,
) {
    @Transactional
    fun redeem(
        code: String,
        clientId: String,
        redirectUri: String,
        codeVerifier: String?,
    ): IssuedProfile {
        val codeHash = Tokens.sha256(code)
        val authorizationCode =
            authorizationCodeRepository.findByCodeHash(codeHash)
                ?: throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code is invalid")

        if (authorizationCode.expiresAt.isBefore(Instant.now())) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code has expired")
        }

        if (authorizationCode.clientId != clientId) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The client_id does not match the issued code")
        }
        if (authorizationCode.redirectUri != redirectUri) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "The redirect_uri does not match the issued code",
            )
        }
        validatePkce(
            authorizationCode.codeChallenge,
            codeVerifier,
        )

        val claimed = authorizationCodeRepository.claim(codeHash, Instant.now())
        if (claimed == 0) {
            logger.warn { "Authorization code already used (possible replay)" }
            throw IndieAuthException(IndieAuthError.Code.INVALID_GRANT, "The authorization code has already been used")
        }

        val scopes = Scopes.parse(authorizationCode.scope)
        return IssuedProfile(
            me = authorizationCode.me,
            scope = authorizationCode.scope,
            profile = profileClaimService.forScopes(scopes),
        )
    }

    private fun validatePkce(
        challenge: String?,
        codeVerifier: String?,
    ) {
        if (challenge.isNullOrBlank()) {
            if (!codeVerifier.isNullOrBlank()) {
                throw IndieAuthException(
                    IndieAuthError.Code.INVALID_GRANT,
                    "No code_verifier is expected for a code issued without a code_challenge",
                )
            }
            return
        }
        if (codeVerifier == null || !Pkce.verify(challenge, codeVerifier)) {
            throw IndieAuthException(
                IndieAuthError.Code.INVALID_GRANT,
                "The code_verifier does not match the code challenge",
            )
        }
    }
}
