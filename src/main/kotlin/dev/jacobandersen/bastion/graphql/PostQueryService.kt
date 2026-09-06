package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.url.UrlService
import org.springframework.stereotype.Service

/**
 * Read queries over published posts for public (unauthenticated) GraphQL use.
 * Lists return only PUBLIC posts; direct lookups additionally allow UNLISTED.
 * PRIVATE and DRAFT posts are never returned here (private is only reachable
 * through the authenticated Micropub source query).
 */
@Service
class PostQueryService(
    private val postService: PostService,
    private val urlService: UrlService,
) {
    private val maxLimit = 100

    fun feed(types: List<PostType>?, limitArg: Int?, offsetArg: Int?): List<Post> {
        val limit = limitArg ?: 10
        val offset = offsetArg ?: 0

        require(limit in 1..maxLimit) { "limit must be an integer between 1 and $maxLimit" }
        require(offset >= 0) { "offset must be a non-negative integer" }
        require(offset % limit == 0) { "offset must be a multiple of limit ($offset % $limit != 0)" }

        return postService.findFeedPosts(types?.map { it.subtype() }, limit, offset)
    }

    fun post(slug: String?, url: String?): Post? {
        val slugToFind = when {
            slug != null && url != null ->
                throw IllegalArgumentException("provide exactly one of slug or url, not both")

            slug != null -> slug
            url != null -> urlService.extractPostSlug(url)
                ?: throw IllegalArgumentException("url is not a URL on this Bastion instance")
            else -> throw IllegalArgumentException("provide either a slug or a url")
        }

        val post = postService.findBySlug(slugToFind) ?: return null
        return post.takeIf { it.isDirectlyReachable }
    }

    private val Post.isDirectlyReachable: Boolean
        get() = !deleted &&
            status == PostStatus.PUBLISHED &&
            (visibility == PostVisibility.PUBLIC || visibility == PostVisibility.UNLISTED)
}
