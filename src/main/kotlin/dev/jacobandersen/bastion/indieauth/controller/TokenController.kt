package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import dev.jacobandersen.bastion.indieauth.service.IndieAuthException
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.TokenResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The IndieAuth token endpoint. Exchanges an authorization code (plus its
 * `client_id`, `redirect_uri` and PKCE verifier) for an access token, or
 * returns an OAuth error.
 */
@RestController
class TokenController(
    private val accessTokenService: AccessTokenService,
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
    ): ResponseEntity<*> =
        try {
            validateRequest(grantType, code, clientId, redirectUri)
            val issued = accessTokenService.exchange(code!!, clientId!!, redirectUri!!, codeVerifier)
            ResponseEntity.ok(
                TokenResponse(
                    accessToken = issued.accessToken,
                    scope = issued.scope.takeIf { it.isNotBlank() },
                    me = issued.me,
                ),
            )
        } catch (e: IndieAuthException) {
            ResponseEntity.status(e.code.status).body(e.toError())
        }

    private fun validateRequest(
        grantType: String?,
        code: String?,
        clientId: String?,
        redirectUri: String?,
    ) {
        if (grantType != "authorization_code") {
            throw IndieAuthException(
                IndieAuthError.Code.UNSUPPORTED_GRANT_TYPE,
                "Only the 'authorization_code' grant is supported",
            )
        }
        if (code.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'code' parameter is required")
        }
        if (clientId.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'client_id' parameter is required")
        }
        if (redirectUri.isNullOrBlank()) {
            throw IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "The 'redirect_uri' parameter is required")
        }
    }
}
