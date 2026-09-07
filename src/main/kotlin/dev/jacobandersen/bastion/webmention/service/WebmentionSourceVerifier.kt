package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2Parser
import dev.jacobandersen.bastion.webmention.http.SourceFetch
import dev.jacobandersen.bastion.webmention.util.HttpUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** The outcome of verifying a received webmention's source document. */
internal enum class SourceVerdict {
    VERIFIED,
    NO_LINK,
    GONE,
    UNREACHABLE,
}

internal data class SourceVerification(
    val verdict: SourceVerdict,
    val parse: Mf2ParseResult? = null,
    val reason: String? = null,
)

/**
 * Verifies that a fetched source document actually mentions the webmention
 * target, following section 3.2.2 of the Webmention specification: an exact
 * match of the target URL is required per media type, a 410 (or 404) means the
 * source is gone, and any other failure is unreachable.
 */
internal object WebmentionSourceVerifier {

    private val URL_ATTRIBUTES: List<Pair<String, String>> = listOf(
        "a" to "href",
        "area" to "href",
        "link" to "href",
        "img" to "src",
        "video" to "src",
        "video" to "poster",
        "audio" to "src",
        "source" to "src",
        "iframe" to "src",
        "object" to "data",
    )

    fun verify(fetch: SourceFetch, targetUrl: String, parser: Mf2Parser): SourceVerification {
        when {
            fetch.statusCode == 410 || fetch.statusCode == 404 ->
                return SourceVerification(SourceVerdict.GONE)

            fetch.statusCode !in 200..299 ->
                return SourceVerification(
                    SourceVerdict.UNREACHABLE,
                    reason = fetch.error
                        ?: "source returned HTTP ${fetch.statusCode}",
                )
        }

        if (HttpUtil.isHtmlContentType(fetch.contentType)) {
            val document = Jsoup.parse(fetch.body, fetch.finalUrl)
            if (!htmlMentions(document, targetUrl)) {
                return SourceVerification(SourceVerdict.NO_LINK, reason = "source does not link to the target")
            }
            return SourceVerification(
                SourceVerdict.VERIFIED,
                parse = parser.parse(fetch.body, fetch.finalUrl),
            )
        }

        if (!fetch.body.contains(targetUrl)) {
            return SourceVerification(SourceVerdict.NO_LINK, reason = "source does not link to the target")
        }
        return SourceVerification(SourceVerdict.VERIFIED)
    }

    private fun htmlMentions(document: Document, targetUrl: String): Boolean {
        val target = targetUrl.trim()
        val normalizedTarget = withoutFragment(target)

        for ((tag, attribute) in URL_ATTRIBUTES) {
            val selector = "$tag[$attribute]"
            for (el in document.select(selector)) {
                val raw = el.attr(attribute)
                if (raw.isBlank()) continue
                val absolute = el.absUrl(attribute)
                if (absolute.isNotEmpty() &&
                    (absolute == target || withoutFragment(absolute) == normalizedTarget)
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun withoutFragment(url: String): String = url.substringBefore('#')
}
