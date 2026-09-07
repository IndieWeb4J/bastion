package dev.jacobandersen.bastion.webmention.util

import dev.jacobandersen.bastion.util.StringUtil.unquote
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

    private fun paramsHasWebmentionRel(params: String): Boolean =
        params.split(';').any { segment ->
            segment.split("=").let { parts ->
                parts.size == 2 && parts[0].trim().equals("rel", ignoreCase = true) && hasWebmentionRel(parts[1].unquote())
            }
        }

    fun hasWebmentionRel(rel: String): Boolean = rel.lowercase().split(Regex("[\\s,]+")).any { it == "webmention" }

    fun resolveEndpoint(
        endpoint: String,
        baseUrl: String,
    ): String? = runCatching { URI(baseUrl).resolve(endpoint).toString() }.getOrNull()

    /**
     * When a discovery response explicitly advertises cacheability, returns the
     * instant at which the discovered endpoint may be reused. Returns null when
     * the target sends no usable cache metadata (or says not to cache), in which
     * case the endpoint must be rediscovered on the next send.
     */
    fun effectiveCacheExpiry(
        cacheControl: String?,
        expiresHeader: String?,
        now: Instant,
    ): Instant? {
        val maxAge =
            cacheControl
                ?.let { Regex("(?:^|,)\\s*max-age\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE).find(it) }
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        if (maxAge != null) {
            return if (maxAge > 0) now.plus(Duration.ofSeconds(maxAge)) else null
        }

        val expires =
            expiresHeader?.let { header ->
                runCatching { ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            }
        return expires?.takeIf { it.isAfter(now) }
    }
}
