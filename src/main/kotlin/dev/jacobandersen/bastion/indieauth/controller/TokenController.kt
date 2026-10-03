package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import dev.jacobandersen.bastion.indieauth.service.IndieAuthException
import dev.jacobandersen.bastion.indieauth.service.ProfileClaimService
import dev.jacobandersen.bastion.indieauth.service.RefreshTokenService
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.Scopes
import dev.jacobandersen.bastion.indieauth.type.TokenResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

/**
 * The IndieAuth token endpoint. Exchanges an authorization code (plus its
 * `client_id`, `redirect_uri` and PKCE verifier) for an access token and a
 * refresh token, or rotates a refresh token for a new access token.
 *
 * Login-only (empty-scope) codes must redeem at the authorization endpoint
 * instead (5.3.3); this endpoint rejects them with `invalid_grant`.
 */
@RestController
class TokenController(
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val profileClaimService: ProfileClaimService,
) {
    @PostMapping(
        path = [IndieAuthEndpoints.TOKEN],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
    )
    fun token(
        @RequestParam("grant_type", required = false) grantType: String?,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("code_verifier", required = false) codeVerifier: String?,
        @RequestParam("refresh_token", required = false) refreshToken: String?,
        @RequestParam("scope", required = false) scope: String?,
    ): ResponseEntity<*> =
        try {
            when (grantType) {
                "authorization_code" -> authorizationCode(code, clientId, redirectUri, codeVerifier)

                "refresh_token" -> refresh(refreshToken, clientId, scope)

                else -> throw IndieAuthException(
                    IndieAuthError.Code.UNSUPPORTED_GRANT_TYPE,
                    "Only the 'authorization_code' and 'refresh_token' grants are supported",
                )
            }
        } catch (e: IndieAuthException) {
            ResponseEntity.status(e.code.status).body(e.toError())
        }

    private fun authorizationCode(
        code: String?,
        clientId: String?,
        redirectUri: String?,
        codeVerifier: String?,
    ): ResponseEntity<*> {
        if (code.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'code' parameter is required")
        }
        if (clientId.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'client_id' parameter is required")
        }
        if (redirectUri.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'redirect_uri' parameter is required")
        }
        val issued = accessTokenService.exchange(code, clientId, redirectUri, codeVerifier)
        val scopes = Scopes.parse(issued.scope)
        val now = Instant.now()
        return ResponseEntity.ok(
            TokenResponse(
                accessToken = issued.accessToken,
                scope = issued.scope.takeIf { it.isNotBlank() },
                me = issued.me,
                expiresIn = maxOf(0, Duration.between(now, issued.expiresAt).seconds),
                profile = profileClaimService.forScopes(scopes),
                refreshToken = refreshTokenService.issue(issued.me, clientId, issued.scope),
            ),
        )
    }

    private fun refresh(
        refreshToken: String?,
        clientId: String?,
        scope: String?,
    ): ResponseEntity<*> {
        if (refreshToken.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'refresh_token' parameter is required")
        }
        if (clientId.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'client_id' parameter is required")
        }
        val rotated = refreshTokenService.refresh(refreshToken, clientId, scope)
        val scopes = Scopes.parse(rotated.scope)
        val now = Instant.now()
        return ResponseEntity.ok(
            TokenResponse(
                accessToken = rotated.accessToken.accessToken,
                scope = rotated.scope.takeIf { it.isNotBlank() },
                me = rotated.accessToken.me,
                expiresIn = maxOf(0, Duration.between(now, rotated.accessToken.expiresAt).seconds),
                profile = profileClaimService.forScopes(scopes),
                refreshToken = rotated.refreshToken,
            ),
        )
    }
}
