package dev.jacobandersen.bastion.indieauth.controller

import dev.jacobandersen.bastion.indieauth.IndieAuthEndpoints
import dev.jacobandersen.bastion.indieauth.type.AuthorizationServerMetadata
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Serves the discovery endpoints clients use to learn Bastion's IndieAuth
 * authorization and token endpoints: RFC 8414 metadata plus the legacy
 * `.well-known` endpoint URL probes.
 */
@RestController
class DiscoveryController(
    @Value($$"${bastion.public-url}") private val publicUrl: String,
) {
    @GetMapping("/.well-known/oauth-authorization-server")
    fun authorizationServer(): AuthorizationServerMetadata =
        AuthorizationServerMetadata(
            issuer = baseUrl,
            authorizationEndpoint = authorizationEndpoint(),
            tokenEndpoint = tokenEndpoint(),
        )

    @GetMapping(
        path = ["/.well-known/oauth-authorization-endpoint"],
        produces = [MediaType.TEXT_PLAIN_VALUE],
    )
    fun authorizationEndpoint(): String = baseUrl + IndieAuthEndpoints.AUTHORIZATION

    @GetMapping(
        path = ["/.well-known/oauth-token-endpoint"],
        produces = [MediaType.TEXT_PLAIN_VALUE],
    )
    fun tokenEndpoint(): String = baseUrl + IndieAuthEndpoints.TOKEN

    private val baseUrl: String
        get() = publicUrl.trimEnd('/')
}
