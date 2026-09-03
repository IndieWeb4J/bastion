package dev.jacobandersen.bastion.micropub.service.get

import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.resp.SourceListResponse
import dev.jacobandersen.bastion.micropub.url.UrlService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.util.MultiValueMap

private val logger = KotlinLogging.logger {}

@Service
class SourceService(
    val postService: PostService,
    private val urlService: UrlService,
) {
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
        logger.info { "Got post: $post" }

        return if (post == null || post.status != PostStatus.PUBLISHED || !post.visibility.canGetByUrl()) {
            ApiResponse.Error.NotFound(errorDescription = "Post not found")
        } else if (post.deleted) {
            ApiResponse.Error.Gone()
        } else {
            val requestedProperties = params["properties"].takeUnless { it.isNullOrEmpty() }

            ApiResponse.Success.Ok(postService.enrichPostFields(post, requestedProperties).post)
        }
    }

    fun handleList(params: Map<String, Array<String>>): ApiResponse<*> {
        val limit = params["limit"]?.firstOrNull()?.toIntOrNull() ?: 10
        val offset = params["offset"]?.firstOrNull()?.toIntOrNull() ?: 0

        val requestedProperties = params["properties"].takeUnless { it.isNullOrEmpty() }
        val posts = postService.findPublicPosts(limit, offset, requestedProperties).map { it.post }

        return ApiResponse.Success.Ok(SourceListResponse(posts))
    }
}