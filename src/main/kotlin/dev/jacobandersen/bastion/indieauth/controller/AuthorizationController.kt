package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.AuthorizationRequest
import dev.jacobandersen.bastion.indieauth.service.AuthorizationService
import dev.jacobandersen.bastion.indieauth.service.CompleteResult
import dev.jacobandersen.bastion.indieauth.service.IndieAuthException
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.util.Redirects
import dev.jacobandersen.bastion.indieauth.util.Uris
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * The IndieAuth authorization endpoint and its callback. The authorization
 * endpoint validates the client's request and redirects the browser to the
 * Herald UI host; the callback receives the browser back from Herald and
 * redirects to the client's `redirect_uri` with Bastion's own authorization
 * code. Neither endpoint renders any UI itself.
 */
@RestController
class AuthorizationController(
    private val authorizationService: AuthorizationService,
) {
    @GetMapping(IndieAuthEndpoints.AUTHORIZATION)
    fun authorize(
        @RequestParam("me", required = false) me: String?,
        @RequestParam("client_id", required = false) clientId: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("scope", required = false) scope: String?,
        @RequestParam("response_type", required = false) responseType: String?,
        @RequestParam("code_challenge", required = false) codeChallenge: String?,
        @RequestParam("code_challenge_method", required = false) codeChallengeMethod: String?,
    ): ResponseEntity<*> {
        val request =
            AuthorizationRequest(
                me = me,
                clientId = clientId,
                redirectUri = redirectUri,
                state = state,
                scope = scope,
                responseType = responseType,
                codeChallenge = codeChallenge,
                codeChallengeMethod = codeChallengeMethod,
            )

        val canRedirect = !redirectUri.isNullOrBlank() && Uris.isRedirectUri(redirectUri)

        return try {
            redirect(authorizationService.begin(request))
        } catch (e: IndieAuthException) {
            if (canRedirect) {
                redirect(Redirects.error(redirectUri, state, e.code.value, e.message))
            } else {
                errorResponse(e)
            }
        }
    }

    @GetMapping(IndieAuthEndpoints.CALLBACK)
    fun callback(
        @RequestParam("state", required = false) state: String?,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("error", required = false) error: String?,
    ): ResponseEntity<*> =
        when (val result = authorizationService.complete(state, code, error)) {
            is CompleteResult.Redirect -> {
                redirect(result.url)
            }

            CompleteResult.Reject -> {
                errorResponse(
                    IndieAuthException(IndieAuthError.Code.INVALID_REQUEST, "Invalid, expired or replayed state"),
                )
            }
        }

    private fun redirect(url: String): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build()

    private fun errorResponse(e: IndieAuthException): ResponseEntity<IndieAuthError> =
        ResponseEntity.status(e.code.status).body(e.toError())
}
