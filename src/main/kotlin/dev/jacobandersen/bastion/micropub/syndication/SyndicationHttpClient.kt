package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.util.StringUtil.excerpt
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

sealed interface SyndicationSendResult {
    data class Success(
        val statusCode: Int,
        val location: String?,
    ) : SyndicationSendResult

    data class Failure(
        val statusCode: Int?,
        val message: String,
    ) : SyndicationSendResult
}

@Service
class SyndicationHttpClient(
    private val objectMapper: ObjectMapper,
) {
    private val client: RestClient =
        RestClient
            .builder()
            .requestFactory(requestFactory())
            .requestInterceptor(SyndicationHttpLoggingInterceptor())
            .build()

    fun sendCreate(
        target: SyndicationConfig.Target,
        obj: Mf2Object,
    ): SyndicationSendResult {
        val body = objectMapper.writeValueAsString(obj)
        return send(target, MediaType.APPLICATION_JSON, body)
    }

    fun sendDelete(
        target: SyndicationConfig.Target,
        sourceUrl: String,
    ): SyndicationSendResult {
        val payload = LinkedMultiValueMap<String, String>()
        payload.add("action", "delete")
        payload.add("url", sourceUrl)
        return send(target, MediaType.APPLICATION_FORM_URLENCODED, payload)
    }

    private fun send(
        target: SyndicationConfig.Target,
        contentType: MediaType,
        body: Any,
    ): SyndicationSendResult =
        try {
            val response =
                client
                    .post()
                    .uri(target.endpoint)
                    .contentType(contentType)
                    .headers { headers -> target.token?.let { headers.setBearerAuth(it) } }
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()

            SyndicationSendResult.Success(
                statusCode = response.statusCode.value(),
                location = response.headers.location?.toString(),
            )
        } catch (e: RestClientResponseException) {
            val statusCode = e.statusCode.value()
            val message = describeHttpError(statusCode, e.responseBodyAsString)
            SyndicationSendResult.Failure(statusCode, message)
        } catch (e: RestClientException) {
            SyndicationSendResult.Failure(null, e.message ?: e::class.simpleName ?: "Request failed")
        }

    private fun describeHttpError(
        statusCode: Int,
        responseBody: String?,
    ): String {
        val body = responseBody?.takeIf { it.isNotBlank() }?.excerpt(MAX_ERROR_BODY_LENGTH)
        return if (body != null) "HTTP $statusCode: $body" else "HTTP $statusCode"
    }

    private fun requestFactory(): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build()

        return JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 10L
        private const val MAX_ERROR_BODY_LENGTH = 2000
    }
}
