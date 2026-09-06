package dev.jacobandersen.bastion.webmention.util

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal object WebmentionUtil {
    private val linkValuePattern = Regex("<([^>]*)>")

    fun findEndpointInLinkHeaders(headerValues: List<String>): String? {
        for (header in headerValues) {
            val uris = linkValuePattern.findAll(header).toList()
            for ((index, match) in uris.withIndex()) {
                val paramsStart = match.range.last + 1
                val paramsEnd = uris.getOrNull(index + 1)?.range?.first ?: header.length
                val params = header.substring(paramsStart, paramsEnd)
                if (paramsHasWebmentionRel(params)) return match.groupValues[1]
            }
        }
        return null
    }

    private fun paramsHasWebmentionRel(params: String): Boolean {
        return params.split(';').any { segment ->
            segment.split("=").let { parts ->
                parts.size == 2 && parts[0].trim().equals("rel", ignoreCase = true) && hasWebmentionRel(parts[1].unquote())
            }
        }
    }

    fun hasWebmentionRel(rel: String): Boolean {
        return rel.lowercase().split(Regex("[\\s,]+")).any { it == "webmention" }
    }

    fun resolveEndpoint(endpoint: String, baseUrl: String): String? {
        return runCatching { URI(baseUrl).resolve(endpoint).toString() }.getOrNull()
    }

    fun computeDiscoveryExpiry(
        cacheControl: String?,
        expiresHeader: String?,
        now: Instant,
        defaultTtlSeconds: Long,
        minCacheSeconds: Long,
    ): Instant {
        val floor = Duration.ofSeconds(minCacheSeconds)

        val maxAge = cacheControl
            ?.let { Regex("(?:^|,)\\s*max-age\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues?.get(1)
            ?.toLongOrNull()
        if (maxAge != null) {
            val ttl = Duration.ofSeconds(maxAge)
            return now.plus(if (ttl > floor) ttl else floor)
        }

        val expires = expiresHeader?.let { header ->
            runCatching { ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
        }
        if (expires != null) {
            return if (expires.isAfter(now.plus(floor))) expires else now.plus(floor)
        }

        return now.plus(Duration.ofSeconds(defaultTtlSeconds))
    }
}
