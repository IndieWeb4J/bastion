package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import dev.jacobandersen.bastion.indieauth.service.ProfileClaimService
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import dev.jacobandersen.bastion.indieauth.type.UserProfile
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

/**
 * User information (IndieAuth 9). Returns the static single-user profile
 * claims for tokens granted the `profile` and/or `email` scopes. Optional
 * endpoint; advertised via `userinfo_endpoint` in server metadata.
 */
@RestController
class UserinfoController(
    private val accessTokenService: AccessTokenService,
    private val profileClaimService: ProfileClaimService,
) {
    @GetMapping(IndieAuthEndpoints.USERINFO)
    fun userinfo(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ResponseEntity<*> {
        val bearer = bearerToken(authorization)
        val issued = bearer?.let { accessTokenService.resolve(it) }
        if (issued == null) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(IndieAuthError.of(IndieAuthError.Code.INVALID_TOKEN, "Valid bearer authorization is required"))
        }
        val lower = issued.scope.map { it.lowercase() }.toSet()
        if ("profile" !in lower && "email" !in lower) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(IndieAuthError.of(IndieAuthError.Code.INSUFFICIENT_SCOPE, "The 'profile' scope is required"))
        }
        return ResponseEntity.ok(profileClaimService.forScopes(issued.scope) ?: UserProfile())
    }

    private fun bearerToken(authorization: String?): String? {
        if (authorization.isNullOrBlank()) return null
        val prefix = "bearer "
        if (!authorization.startsWith(prefix, ignoreCase = true)) return null
        return authorization.substring(prefix.length).trim().takeIf { it.isNotBlank() }
    }
}
