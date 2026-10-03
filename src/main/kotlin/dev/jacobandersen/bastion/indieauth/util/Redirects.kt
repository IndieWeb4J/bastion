package dev.jacobandersen.bastion.indieauth.util

import org.springframework.web.util.UriComponentsBuilder

/**
 * Builds the redirects the IndieAuth endpoints produce back to a client's
 * `redirect_uri`: a successful one carrying `code` + `state` + `iss`
 * (the issuer identifier, REQUIRED per 5.2.1 for mix-up protection), and an
 * error one carrying `error` (+ optional `error_description`) + `state`.
 */
object Redirects {
    fun code(
        redirectUri: String,
        state: String?,
        code: String,
        issuer: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(redirectUri)
            .queryParam("code", code)
            .queryParam("state", state)
            .queryParam("iss", issuer)
            .build()
            .encode()
            .toUriString()

    fun error(
        redirectUri: String,
        state: String?,
        error: String,
        description: String? = null,
    ): String {
        val builder =
            UriComponentsBuilder
                .fromUriString(redirectUri)
                .queryParam("error", error)
        if (!description.isNullOrBlank()) {
            builder.queryParam("error_description", description)
        }
        if (state != null) {
            builder.queryParam("state", state)
        }
        return builder.build().encode().toUriString()
    }
}
