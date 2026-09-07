package dev.jacobandersen.bastion.webmention.util

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

internal object HttpUtil {
    internal fun isHtmlContentType(contentType: String?): Boolean {
        return contentType == null
                || contentType.startsWith("text/html", ignoreCase = true)
                || contentType.startsWith("application/xhtml+xml", ignoreCase = true)
    }

    internal fun isTransientStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599
    }

    /**
     * Whether the URL's host must not be contacted: non-http(s) schemes and
     * hosts resolving to loopback, link-local, site-local/private, ULA,
     * multicast or unspecified addresses.
     *
     * [failClosedOnDnsError] controls what happens when the host cannot be
     * resolved: `true` (used when fetching untrusted URLs) treats it as
     * blocked; `false` (used for outbound sends to discovered targets) lets
     * the request proceed so a transient DNS failure does not silently drop a
     * send.
     */
    internal fun isBlockedHost(url: String, failClosedOnDnsError: Boolean): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return true

        val scheme = uri.scheme?.lowercase() ?: return true
        if (scheme != "http" && scheme != "https") return true

        val host = uri.host ?: return true

        val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return true

        val addresses = runCatching { InetAddress.getAllByName(normalized) }.getOrNull()
            ?: return failClosedOnDnsError

        return addresses.any(::isDisallowedAddress)
    }

    private fun isDisallowedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        if (address is Inet4Address) return false
        if (address is Inet6Address) {
            // fc00::/7 (unique local addresses) are not covered by isSiteLocalAddress.
            val firstByte = address.address[0].toInt() and 0xfe
            return firstByte == 0xfc
        }
        return true
    }
}
