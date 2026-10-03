package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import dev.jacobandersen.bastion.indieauth.service.RefreshTokenService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Token revocation (IndieAuth 7, RFC 7009). Revokes the presented access or
 * refresh token. Always responds 200, including for unknown tokens, so callers
 * learn nothing about token validity here; use introspection for that.
 */
@RestController
class RevocationController(
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService,
) {
    @PostMapping(
        path = [IndieAuthEndpoints.REVOCATION],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
    )
    fun revoke(
        @RequestParam("token", required = false) token: String?,
        @RequestParam("token_type_hint", required = false) tokenTypeHint: String?,
    ): ResponseEntity<Void> {
        if (!token.isNullOrBlank()) {
            accessTokenService.revoke(token)
            refreshTokenService.revoke(token)
        }
        return ResponseEntity.ok().build()
    }
}
