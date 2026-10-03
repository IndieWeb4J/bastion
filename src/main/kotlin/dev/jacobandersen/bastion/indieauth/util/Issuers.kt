package dev.jacobandersen.bastion.indieauth.util

import java.net.URI

/**
 * Normalizes Bastion's issuer identifier per IndieAuth 3.1 (and RFC 9207):
 * an `https` URL with no query or fragment that is a prefix of the
 * `indieauth-metadata` URL. Root issuers use a trailing slash
 * (`https://example.com/`); issuers with a path keep the path without a
 * trailing slash (`https://example.com/sub`).
 */
object Issuers {
    fun issuer(publicUrl: String): String {
        val trimmed = publicUrl.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "bastion.public-url must not be blank" }
        val uri =
            runCatching { URI(trimmed) }.getOrNull()
                ?: throw IllegalArgumentException("bastion.public-url is not a valid URL: $publicUrl")
        require(uri.scheme?.lowercase() == "https") {
            "IndieAuth issuer must use the https scheme: $publicUrl"
        }
        require(uri.query == null && uri.fragment == null) {
            "IndieAuth issuer must not contain a query or fragment: $publicUrl"
        }
        require(uri.userInfo.isNullOrBlank()) {
            "IndieAuth issuer must not contain user info: $publicUrl"
        }
        val host = uri.host ?: throw IllegalArgumentException("IndieAuth issuer must have a host: $publicUrl")
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        val path = uri.path.orEmpty()
        return if (path.isBlank() || path == "/") {
            "https://${host.lowercase()}$port/"
        } else {
            "https://${host.lowercase()}$port/${path.trim('/')}"
        }
    }

    fun baseUrl(publicUrl: String): String = publicUrl.trim().trimEnd('/')
}
