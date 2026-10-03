package dev.jacobandersen.bastion.indieauth.util

import dev.jacobandersen.bastion.url.UrlNormalizer
import java.net.URI

/**
 * Strict URL validation for IndieAuth identifiers (spec sections 3.2 and 3.3).
 * [Uris.isRedirectUri] remains the lenient base check for `redirect_uri`
 * (absolute http(s), no fragment); the functions here enforce the
 * per-identifier rules the authorization endpoint must apply.
 */
object IndieAuthUrls {
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun isValidProfileUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (isIpLiteral(host)) return false
        if (uri.port != -1) return false
        if (!uri.userInfo.isNullOrBlank()) return false
        if (uri.fragment != null) return false
        // Per 3.4 a missing path canonicalizes to "/".
        val path = uri.path.orEmpty().ifEmpty { "/" }
        if (hasDotSegment(path)) return false
        return true
    }

    fun isValidClientId(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (isIpLiteral(host) && !isLoopbackLiteral(host)) return false
        if (!uri.userInfo.isNullOrBlank()) return false
        if (uri.fragment != null) return false
        // Per 3.4 a missing path canonicalizes to "/".
        val path = uri.path.orEmpty().ifEmpty { "/" }
        if (hasDotSegment(path)) return false
        return true
    }

    fun isLoopbackHost(host: String): Boolean {
        val lower = host.lowercase().trim('[', ']')
        return lower == "localhost" || lower == "127.0.0.1" || lower == "::1"
    }

    /** True when the redirect target differs in scheme, host or port from the client. */
    fun isCrossHost(
        clientId: String,
        redirectUri: String,
    ): Boolean {
        val client = UrlNormalizer.authority(clientId) ?: return true
        val redirect = UrlNormalizer.authority(redirectUri) ?: return true
        return client != redirect
    }

    private fun isIpLiteral(host: String): Boolean {
        val stripped = host.trim('[', ']')
        if (IPV4.matches(stripped)) return true
        return ':' in stripped
    }

    private fun isLoopbackLiteral(host: String): Boolean = isLoopbackHost(host)

    private fun hasDotSegment(path: String): Boolean = path.split('/').any { it == "." || it == ".." }
}
