package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.SourceListResponse
import dev.jacobandersen.bastion.micropub.url.UrlService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class SourceService(
    val postService: PostService,
    private val urlService: UrlService,
) {
    private val maxLimit = 100

    fun getSource(params: MutableMap<String, Array<String>>): ApiResponse<*> {
        val url = params["url"]?.firstOrNull()
        params.remove("url")

        return if (url == null) {
            handleList(params)
        } else {
            handleOne(url, params)
        }
    }

    fun handleOne(url: String, params: Map<String, Array<String>>): ApiResponse<*> {
        val slug = urlService.extractPostSlug(url)
            ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Invalid URL for this Bastion instance: $url")

        logger.info { "Will look up post by slug $slug" }

        val post = postService.findBySlug(slug)
            ?: return ApiResponse.Error.InvalidRequest(errorDescription = "Post not found for URL: $url")

        if (post.deleted) {
            return ApiResponse.Error.Gone()
        }

        val requestedProperties = params["properties"].takeUnless { it.isNullOrEmpty() }
        val filtered = postService.filterPostFields(post, requestedProperties)

        // Per spec: when specific properties are requested the response is a
        // bare properties object; otherwise a full mf2 object.
        return if (requestedProperties == null) {
            ApiResponse.Success.Ok(filtered.post)
        } else {
            ApiResponse.Success.Ok(mapOf("properties" to filtered.post.properties))
        }
    }

    fun handleList(params: Map<String, Array<String>>): ApiResponse<*> {
        val limit = params["limit"]?.firstOrNull()
        val offset = params["offset"]?.firstOrNull()

        val parsedLimit = limit?.toIntOrNull()
        val parsedOffset = offset?.toIntOrNull()

        if (limit != null && (parsedLimit == null || parsedLimit < 1 || parsedLimit > maxLimit)) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "limit must be an integer between 1 and $maxLimit")
        }
        if (offset != null && (parsedOffset == null || parsedOffset < 0)) {
            return ApiResponse.Error.InvalidRequest(errorDescription = "offset must be a non-negative integer")
        }

        val effectiveLimit = parsedLimit ?: 10
        val effectiveOffset = parsedOffset ?: 0

        if (effectiveOffset % effectiveLimit != 0) {
            return ApiResponse.Error.InvalidRequest(
                errorDescription = "offset must be a multiple of limit ($effectiveOffset % $effectiveLimit != 0)"
            )
        }

        val requestedProperties = params["properties"].takeUnless { it.isNullOrEmpty() }
        val posts = postService.findPublicPosts(effectiveLimit, effectiveOffset, requestedProperties).map { it.post }

        return ApiResponse.Success.Ok(SourceListResponse(posts))
    }
}
