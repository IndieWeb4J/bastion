package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.IndieAuthException
import dev.jacobandersen.bastion.indieauth.service.ProfileUrlService
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.ProfileUrlResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The IndieAuth authorization-endpoint code redemption (5.3.2): exchanges an
 * authorization code for the canonical profile URL and optional profile
 * information. Used by clients that only need to know who logged in. Never
 * returns an access token; scoped clients redeem at the token endpoint.
 */
@RestController
class ProfileUrlController(
    private val profileUrlService: ProfileUrlService,
) {
    @PostMapping(
        path = [IndieAuthEndpoints.AUTHORIZATION],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
    )
    fun redeem(
        @RequestParam("grant_type", required = false) grantType: String?,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("code_verifier", required = false) codeVerifier: String?,
    ): ResponseEntity<*> =
        try {
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
            val issued = profileUrlService.redeem(code, clientId, redirectUri, codeVerifier)
            ResponseEntity.ok(ProfileUrlResponse(me = issued.me, profile = issued.profile))
        } catch (e: IndieAuthException) {
            ResponseEntity.status(e.code.status).body(e.toError())
        }
}
