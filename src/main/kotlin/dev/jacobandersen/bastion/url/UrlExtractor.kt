package dev.jacobandersen.bastion.url

import java.net.URI
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType

internal object UrlExtractor {
    private val linkExtractor = LinkExtractor.builder()
        .linkTypes(setOf(LinkType.URL))
        .build()

    fun distinctUrls(texts: List<String>): List<String> {
        return texts
            .flatMap { text ->
                linkExtractor.extractLinks(text).mapNotNull { span ->
                    val url = text.substring(span.beginIndex, span.endIndex)
                    canonicalKey(url)?.let { key -> url to key }
                }
            }
            .distinctBy { (_, key) -> key }
            .map { (url, _) -> url }
    }

    private fun canonicalKey(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val port = when (uri.port) {
            -1, 80, 443 -> ""
            else -> ":${uri.port}"
        }
        val path = (uri.normalize().rawPath ?: "").trimEnd('/')
        val query = canonicalQuery(uri.rawQuery)
        return "$host$port$path$query"
    }

    private fun canonicalQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val cleaned = rawQuery
            .split('&')
            .filter { it.isNotEmpty() }
            .filterNot { it.substringBefore('=').lowercase().startsWith("utm_") }
            .sorted()
            .joinToString("&")
        return if (cleaned.isEmpty()) "" else "?$cleaned"
    }
}
