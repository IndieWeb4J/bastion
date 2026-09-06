package dev.jacobandersen.bastion.webmention.util

import java.net.InetAddress
import java.net.URI

internal object HttpUtil {
    internal fun isHtmlContentType(contentType: String?): Boolean {
        return contentType == null
                || contentType.startsWith("text/html")
                || contentType.startsWith("application/xhtml+xml")
    }

    internal fun isTransientStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599
    }

    internal fun isLoopbackOrLocal(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host ?: return false

        val normalized = host.removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return true

        val addresses = runCatching { InetAddress.getAllByName(normalized) }.getOrNull() ?: return false
        return addresses.any { it.isLoopbackAddress || it.isAnyLocalAddress || it.isLinkLocalAddress }
    }
}