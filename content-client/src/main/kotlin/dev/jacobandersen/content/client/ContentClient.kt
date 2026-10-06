package dev.jacobandersen.content.client

import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.ObjectMapper
import java.io.InputStream

/**
 * Client facade for the Bastion content service. Implements [ContentReadClient]
 * (open to any service) and [ContentWriteClient] (writers holding the service
 * token) so a caller injects only the capability it needs.
 *
 * The [ContentWriteClient] boundary is mf2; see [CreatePostCommand].
 */
class ContentClient(
    private val properties: ContentClientProperties,
    private val objectMapper: ObjectMapper,
    private val restClient: RestClient = defaultRestClient(properties),
) : ContentReadClient,
    ContentWriteClient {
    private val baseUrl: String = properties.baseUrl.trimEnd('/')

    // -------------------------------------------------------------------- read

    override fun postBySlug(slug: String): PostDto? = getOrNull { builder -> builder.path("/internal/posts").queryParam("slug", slug) }

    override fun postByUrl(url: String): PostDto? = getOrNull { builder -> builder.path("/internal/posts").queryParam("url", url) }

    override fun postById(id: String): PostDto? = getOrNull { builder -> builder.path("/internal/posts/$id") }

    override fun changedSince(
        cursor: String?,
        limit: Int,
    ): ChangedPostsPage =
        execute {
            restClient
                .get()
                .uri(
                    UriComponentsBuilder
                        .fromUriString("$baseUrl/internal/posts/changed")
                        .queryParam("limit", limit)
                        .apply { if (cursor != null) queryParam("cursor", cursor) }
                        .build(true)
                        .toUriString(),
                ).headers(::authorize)
                .retrieve()
                .body(ChangedPostsPage::class.java)
        } ?: ChangedPostsPage(emptyList(), null)

    // ------------------------------------------------------------------- write

    override fun create(command: CreatePostCommand): WritePostResult =
        execute {
            restClient
                .post()
                .uri("$baseUrl/internal/posts")
                .headers(::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(command))
                .retrieve()
                .body(WritePostResult::class.java)
        } ?: throw ContentClientException("Content service returned an empty create result")

    override fun update(
        id: String,
        command: UpdatePostCommand,
    ): WritePostResult =
        execute {
            restClient
                .patch()
                .uri("$baseUrl/internal/posts/$id")
                .headers(::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(command))
                .retrieve()
                .body(WritePostResult::class.java)
        } ?: throw ContentClientException("Content service returned an empty update result")

    override fun delete(id: String): WritePostResult = lifecycle(id, "delete")

    override fun undelete(id: String): WritePostResult = lifecycle(id, "undelete")

    override fun uploadMedia(
        filename: String,
        contentType: String?,
        bytes: InputStream,
    ): MediaUploadResult {
        val resource =
            object : InputStreamResource(bytes) {
                override fun getFilename(): String = filename

                override fun contentLength(): Long = -1
            }
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", resource)

        return execute {
            restClient
                .post()
                .uri("$baseUrl/internal/media")
                .headers(::authorize)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(MediaUploadResult::class.java)
        } ?: throw ContentClientException("Content service returned an empty media result")
    }

    // ---------------------------------------------------------------- helpers

    private fun lifecycle(
        id: String,
        action: String,
    ): WritePostResult =
        execute {
            restClient
                .post()
                .uri("$baseUrl/internal/posts/$id/$action")
                .headers(::authorize)
                .retrieve()
                .body(WritePostResult::class.java)
        } ?: throw ContentClientException("Content service returned an empty $action result")

    private inline fun getOrNull(configure: (UriComponentsBuilder) -> UriComponentsBuilder): PostDto? =
        try {
            val uri =
                configure(UriComponentsBuilder.fromUriString(baseUrl))
                    .build(true)
                    .toUriString()
            restClient
                .get()
                .uri(uri)
                .headers(::authorize)
                .retrieve()
                .body(PostDto::class.java)
        } catch (e: RestClientResponseException) {
            if (e.statusCode.value() ==
                404
            ) {
                null
            } else {
                throw ContentClientException("Content request failed (HTTP ${e.statusCode.value()})", e)
            }
        } catch (e: Exception) {
            throw ContentClientException("Content request failed: ${e.message}", e)
        }

    private fun authorize(headers: HttpHeaders) {
        if (properties.serviceToken.isNotBlank()) {
            headers.setBearerAuth(properties.serviceToken)
        }
    }

    private inline fun <T> execute(block: () -> T): T =
        try {
            block()
        } catch (e: RestClientResponseException) {
            throw ContentClientException("Content request failed (HTTP ${e.statusCode.value()})", e)
        } catch (e: Exception) {
            throw ContentClientException("Content request failed: ${e.message}", e)
        }

    companion object {
        private fun defaultRestClient(properties: ContentClientProperties): RestClient =
            RestClient
                .builder()
                .baseUrl(properties.baseUrl.trimEnd('/'))
                .requestFactory(
                    SimpleClientHttpRequestFactory().apply {
                        setConnectTimeout(properties.connectTimeout)
                        setReadTimeout(properties.readTimeout)
                    },
                ).build()
    }
}

/** Thrown when a content-service call fails or returns an unexpected response. */
class ContentClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
