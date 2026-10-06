package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.util.StringUtil.excerpt
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
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

data class SyndicationUpdate(
    val replace: String?,
    val add: String?,
    val delete: String?,
)

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

    fun sendUpdate(
        target: SyndicationConfig.Target,
        sourceUrl: String,
        update: SyndicationUpdate,
    ): SyndicationSendResult {
        val body: MutableMap<String, Any> =
            linkedMapOf(
                "action" to "update",
                "url" to sourceUrl,
            )
        update.replace?.let { body["replace"] = objectMapper.readTree(it) }
        update.add?.let { body["add"] = objectMapper.readTree(it) }
        update.delete?.let { body["delete"] = objectMapper.readTree(it) }
        return send(target, MediaType.APPLICATION_JSON, objectMapper.writeValueAsString(body))
    }

    fun serializeUpdate(update: MicropubUpdatePayload): SyndicationUpdate =
        SyndicationUpdate(
            replace = update.replacements?.let { objectMapper.writeValueAsString(it) },
            add = update.additions?.let { objectMapper.writeValueAsString(it) },
            delete =
                update.removals?.let { removals ->
                    when (removals) {
                        is MicropubUpdatePayload.Removals.All -> objectMapper.writeValueAsString(removals.properties)
                        is MicropubUpdatePayload.Removals.Many -> objectMapper.writeValueAsString(removals.properties)
                    }
                },
        )

    /**
     * Rewrites an outgoing update so the downstream copy stays an excerpt plus
     * permalink: `content`/`summary` entries in `replace`/`add` are replaced
     * with [excerptText]. When [titleInExcerpt] is true (article posts, whose
     * syndicated text leads with the title) and the update renames the post
     * without touching its content, a `content` replace is added so the
     * downstream title does not go stale. Unparseable sections are passed
     * through untouched.
     */
    fun mapUpdateToExcerpt(
        update: SyndicationUpdate,
        excerptText: String,
        titleInExcerpt: Boolean,
    ): SyndicationUpdate {
        val replaceNode = update.replace?.let(::parseObjectOrNull)
        val addNode = update.add?.let(::parseObjectOrNull)
        if ((update.replace != null && replaceNode == null) || (update.add != null && addNode == null)) {
            return update
        }

        val mentionsName = listOfNotNull(replaceNode, addNode).any { it.has("name") }
        val mentionsContent = listOfNotNull(replaceNode, addNode).any { it.has("content") }
        replaceNode?.let { rewriteContentEntries(it, excerptText) }
        addNode?.let { rewriteContentEntries(it, excerptText) }

        var replace = replaceNode?.let { objectMapper.writeValueAsString(it) }
        if (titleInExcerpt && mentionsName && !mentionsContent) {
            val node = replaceNode ?: objectMapper.createObjectNode()
            node.set("content", objectMapper.createArrayNode().add(excerptText))
            replace = objectMapper.writeValueAsString(node)
        }

        return update.copy(
            replace = replace,
            add = addNode?.let { objectMapper.writeValueAsString(it) },
        )
    }

    private fun parseObjectOrNull(json: String): ObjectNode? {
        val node = runCatching { objectMapper.readTree(json) }.getOrNull()
        return node as? ObjectNode
    }

    private fun rewriteContentEntries(
        node: ObjectNode,
        excerptText: String,
    ) {
        if (node.has("content")) {
            node.set("content", objectMapper.createArrayNode().add(excerptText))
        }
        if (node.has("summary")) {
            node.set("summary", objectMapper.createArrayNode().add(excerptText))
        }
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
