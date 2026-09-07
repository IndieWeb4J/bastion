package dev.jacobandersen.bastion.webmention.http

import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import dev.jacobandersen.bastion.webmention.util.HttpUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/** Decides whether a URL's host must not be contacted while fetching a source. */
fun interface SourceHostValidator {
    fun isBlocked(url: String): Boolean
}

/** Blocks loopback, link-local and private/ULA hosts, failing closed on DNS errors. */
@Component
class DefaultSourceHostValidator : SourceHostValidator {
    override fun isBlocked(url: String): Boolean = HttpUtil.isBlockedHost(url, failClosedOnDnsError = true)
}

/**
 * The outcome of fetching a webmention source URL for verification: the final
 * HTTP status after redirects, the resolved URL, content type and body. On a
 * network failure [statusCode] is 0 and [error] carries the cause.
 */
data class SourceFetch(
    val statusCode: Int,
    val finalUrl: String,
    val contentType: String?,
    val body: String,
    val error: String? = null,
)

/**
 * Fetches a webmention source document for verification. Redirects are
 * followed manually so the host of every hop is validated by the
 * [SourceHostValidator]; blindly following redirects would let a public URL
 * redirect to an internal one (SSRF).
 */
@Service
class WebmentionSourceFetcher(
    private val config: WebmentionConfig,
    private val hostValidator: SourceHostValidator,
) {
    fun fetch(sourceUrl: String): SourceFetch {
        logger.info { "Fetching webmention source $sourceUrl for verification" }
        return runCatching { fetchFollowingRedirects(sourceUrl) }
            .getOrElse { error ->
                logger.warn { "Fetching webmention source $sourceUrl failed: ${error.message}" }
                SourceFetch(
                    statusCode = 0,
                    finalUrl = sourceUrl,
                    contentType = null,
                    body = "",
                    error = error.message,
                )
            }
    }

    private fun fetchFollowingRedirects(startUrl: String): SourceFetch {
        var current = startUrl
        repeat(MAX_REDIRECTS) {
            if (hostValidator.isBlocked(current)) {
                return SourceFetch(0, current, null, "", "source host resolves to a blocked address")
            }

            val response = Jsoup.connect(current)
                .userAgent(USER_AGENT)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .maxBodySize(MAX_BODY_SIZE)
                .timeout((config.readTimeoutSeconds * 1000).toInt())
                .execute()

            val status = response.statusCode()
            if (status in REDIRECT_STATUSES) {
                val location = response.header("Location")?.trim().orEmpty()
                if (location.isEmpty()) {
                    return SourceFetch(status, current, response.contentType(), "", "redirect without a Location header")
                }
                current = runCatching { URI(current).resolve(location).toString() }.getOrNull()
                    ?: return SourceFetch(status, current, response.contentType(), "", "redirect Location is not a valid URL")
                return@repeat
            }

            return SourceFetch(
                statusCode = status,
                finalUrl = response.url().toExternalForm(),
                contentType = response.contentType(),
                body = response.body(),
            )
        }
        return SourceFetch(0, current, null, "", "too many redirects")
    }

    companion object {
        const val USER_AGENT = "BastionWebmentionReceiver/0.0.1"
        const val MAX_BODY_SIZE = 1_000_000
        const val MAX_REDIRECTS = 10
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
    }
}
