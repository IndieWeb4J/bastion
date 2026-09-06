package dev.jacobandersen.bastion.webmention.http

import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

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

@Service
class WebmentionSourceFetcher(
    private val config: WebmentionConfig,
) {
    fun fetch(sourceUrl: String): SourceFetch {
        logger.info { "Fetching webmention source $sourceUrl for verification" }
        return runCatching {
            val response = Jsoup.connect(sourceUrl)
                .userAgent(USER_AGENT)
                .followRedirects(true)
                .maxBodySize(MAX_BODY_SIZE)
                .timeout((config.readTimeoutSeconds * 1000).toInt())
                .execute()

            SourceFetch(
                statusCode = response.statusCode(),
                finalUrl = response.url().toExternalForm(),
                contentType = response.contentType(),
                body = response.body(),
            )
        }.getOrElse { error ->
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

    companion object {
        const val USER_AGENT = "BastionWebmentionReceiver/0.0.1"
        const val MAX_BODY_SIZE = 1_000_000
    }
}
