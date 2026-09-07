package dev.jacobandersen.bastion.webmention.http

import dev.jacobandersen.bastion.util.StringUtil.excerpt
import dev.jacobandersen.bastion.webmention.config.WebmentionConfig
import dev.jacobandersen.bastion.webmention.util.HttpUtil
import dev.jacobandersen.bastion.webmention.util.HttpUtil.isTransientStatus
import dev.jacobandersen.bastion.webmention.util.WebmentionUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient
import java.time.Duration
import kotlin.collections.firstOrNull

private val logger = KotlinLogging.logger {}

sealed interface SendWebmentionResult {
    data class Success(
        val statusCode: Int,
    ) : SendWebmentionResult

    data class Failure(
        val statusCode: Int?,
        val message: String,
        val retryable: Boolean,
    ) : SendWebmentionResult
}

data class EndpointDiscovery(
    val endpointUrl: String?,
    val cacheControl: String?,
    val expiresHeader: String?,
)

@Service
class WebmentionHttpClient(
    private val config: WebmentionConfig,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory(config))
            .requestInterceptor(WebmentionHttpLoggingInterceptor())
            .build()

    private fun requestFactory(config: WebmentionConfig): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(config.connectTimeoutSeconds))
                .build()

        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(config.readTimeoutSeconds))
        }
    }

    internal fun sendWebmention(
        sourceUrl: String,
        targetUrl: String,
        endpointUrl: String,
    ): SendWebmentionResult {
        val payload = LinkedMultiValueMap<String, String>()
        payload.add("source", sourceUrl)
        payload.add("target", targetUrl)

        return try {
            val statusCode =
                client
                    .post()
                    .uri(endpointUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .statusCode
                    .value()

            if (statusCode in 200..299) {
                SendWebmentionResult.Success(statusCode)
            } else {
                SendWebmentionResult.Failure(statusCode, "HTTP $statusCode", isTransientStatus(statusCode))
            }
        } catch (e: RestClientResponseException) {
            val statusCode = e.statusCode.value()
            val message = describeHttpError(statusCode, e.responseBodyAsString)
            SendWebmentionResult.Failure(statusCode, message, isTransientStatus(statusCode))
        } catch (e: RestClientException) {
            SendWebmentionResult.Failure(null, e.message ?: e::class.simpleName ?: "Request failed", true)
        }
    }

    private fun describeHttpError(
        statusCode: Int,
        responseBody: String?,
    ): String {
        val body = responseBody?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) "HTTP $statusCode: $body" else "HTTP $statusCode"
    }

    internal fun discoverWebmentionEndpoint(url: String): EndpointDiscovery {
        logger.info { "Discovering webmention endpoint for $url" }
        return runCatching {
            val res =
                Jsoup
                    .connect(url)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .timeout((config.readTimeoutSeconds * 1000).toInt())
                    .execute()

            val cacheControl = firstHeader(res, "Cache-Control")
            val expires = firstHeader(res, "Expires")

            val baseUrl = res.url().toExternalForm()

            val linkEndpoint =
                WebmentionUtil
                    .findEndpointInLinkHeaders(res.headers(HttpHeaders.LINK))
                    ?.let { WebmentionUtil.resolveEndpoint(it, baseUrl) }

            if (linkEndpoint != null) {
                logger.info { "Found valid HTTP Link: $linkEndpoint" }
                return EndpointDiscovery(linkEndpoint, cacheControl, expires)
            }

            if (!HttpUtil.isHtmlContentType(res.contentType())) {
                logger.info { "No valid HTTP Link found, and document is not HTML, cannot resolve webmention endpoint" }
                return EndpointDiscovery(null, cacheControl, expires)
            }

            logger.info { "No HTTP Link found, checking HTML document..." }
            val htmlEndpoint =
                res
                    .parse()
                    .select("link[href], a[href]")
                    .firstOrNull { element ->
                        WebmentionUtil.hasWebmentionRel(element.attr("rel")) && element.absUrl("href").isNotBlank()
                    }?.absUrl("href")

            if (htmlEndpoint != null) {
                logger.info { "Found valid HTML endpoint: $htmlEndpoint" }
                return EndpointDiscovery(htmlEndpoint, cacheControl, expires)
            }

            logger.info { "Could not resolve webmention endpoint for $url" }
            EndpointDiscovery(null, cacheControl, expires)
        }.getOrElse {
            logger.warn { "Webmention endpoint discovery failed for $url: ${it.message}" }
            EndpointDiscovery(null, null, null)
        }
    }

    private fun firstHeader(
        res: org.jsoup.Connection.Response,
        name: String,
    ): String? = res.headers(name).firstOrNull()

    companion object {
        const val USER_AGENT = "BastionWebmentionHttpClient/0.0.1"
        const val MAX_ERROR_BODY_LENGTH = 2000
    }
}
