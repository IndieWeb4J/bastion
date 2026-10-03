package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.service.AccessTokenService
import dev.jacobandersen.bastion.indieauth.service.IntrospectionService
import dev.jacobandersen.bastion.indieauth.type.IndieAuthError
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Token introspection for resource servers (IndieAuth 6, RFC 7662 plus `me`).
 * The endpoint requires authorization with any active Bastion-issued access
 * token; insufficient authorization yields 401. Unknown or expired subject
 * tokens yield 200 with `active: false` and no further detail.
 */
@RestController
class IntrospectionController(
    private val accessTokenService: AccessTokenService,
    private val introspectionService: IntrospectionService,
) {
    @PostMapping(
        path = [IndieAuthEndpoints.INTROSPECTION],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
    )
    fun introspect(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestParam("token", required = false) token: String?,
    ): ResponseEntity<*> {
        val bearer =
            authorization
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring("Bearer ".length)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        if (bearer.isNullOrBlank() || accessTokenService.resolve(bearer) == null) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(IndieAuthError.of(IndieAuthError.Code.INVALID_TOKEN, "Valid bearer authorization is required"))
        }
        if (token.isNullOrBlank()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(IndieAuthError.of(IndieAuthError.Code.INVALID_REQUEST, "The 'token' parameter is required"))
        }
        return ResponseEntity.ok(introspectionService.introspect(token))
    }
}
