package dev.jacobandersen.bastion.content.url

import java.net.URI

/**
 * Shared URL normalization for comparing URLs across the application
 * (webmention target dedup, profile identity checks, slug extraction).
 */
internal object UrlNormalizer {
    data class Authority(
        val scheme: String,
        val host: String,
        val port: Int,
    )

    /**
     * Lowercased scheme/host with default ports normalized to -1, or null for
     * invalid or non-http(s) URLs.
     */
    fun authority(url: String): Authority? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val port =
            when (uri.port) {
                -1, 80, 443 -> -1
                else -> uri.port
            }
        return Authority(scheme, host, port)
    }

    /**
     * An identity key for a URL: `scheme://host[:port]/path` with scheme and
     * host lowercased, default ports dropped, the fragment removed and the
     * trailing slash trimmed. The query string is not part of the identity.
     */
    fun identity(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val authority = authority(url) ?: return null
        val port = if (authority.port == -1) "" else ":${authority.port}"
        val path = (uri.path ?: "").trimEnd('/')
        return "${authority.scheme}://${authority.host}$port$path"
    }

    /**
     * A dedup key for a URL: authority plus normalized path plus a
     * canonicalized query string (sorted parameters, tracking parameters
     * removed), without the scheme.
     */
    fun dedupKey(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val authority = authority(url) ?: return null
        val port = if (authority.port == -1) "" else ":${authority.port}"
        val path = (uri.normalize().rawPath ?: "").trimEnd('/')
        val query = canonicalQuery(uri.rawQuery)
        return "${authority.host}$port$path$query"
    }

    private fun canonicalQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val cleaned =
            rawQuery
                .split('&')
                .filter { it.isNotEmpty() }
                .filterNot { it.substringBefore('=').lowercase().startsWith("utm_") }
                .sorted()
                .joinToString("&")
        return if (cleaned.isEmpty()) "" else "?$cleaned"
    }
}
