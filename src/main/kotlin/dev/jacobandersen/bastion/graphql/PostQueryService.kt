package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostType
import dev.jacobandersen.bastion.micropub.type.subtype
import dev.jacobandersen.bastion.url.UrlService
import org.springframework.stereotype.Service

/**
 * Read queries over published posts for public (unauthenticated) GraphQL use.
 * Lists return only PUBLIC posts; direct lookups additionally allow UNLISTED.
 * PRIVATE and DRAFT posts are never returned here (private is only reachable
 * through the authenticated Micropub source query). A post that was publicly
 * reachable and is now soft-deleted surfaces as [PostGone].
 */
@Service
class PostQueryService(
    private val postService: PostService,
    private val urlService: UrlService,
) {
    fun feed(types: List<PostType>?, limitArg: Int?, offsetArg: Int?): List<Post> {
        val limit = limitArg ?: 10
        val offset = offsetArg ?: 0

        require(limit in 1..PostService.MAX_PAGE_SIZE) { "limit must be an integer between 1 and ${PostService.MAX_PAGE_SIZE}" }
        require(offset >= 0) { "offset must be a non-negative integer" }
        require(offset % limit == 0) { "offset must be a multiple of limit ($offset % $limit != 0)" }

        return postService.findFeedPosts(types?.map { it.subtype() }, limit, offset)
    }

    /**
     * Resolves a direct post lookup. Returns null when the post does not exist
     * or was never publicly reachable (draft/private); [PostLookupResult.Gone]
     * when the post existed publicly and has since been deleted.
     */
    fun post(slug: String?, url: String?): PostLookupResult? {
        val slugToFind = when {
            slug != null && url != null ->
                throw IllegalArgumentException("provide exactly one of slug or url, not both")

            slug != null -> slug
            url != null -> urlService.extractPostSlug(url)
                ?: throw IllegalArgumentException("url is not a URL on this Bastion instance")
            else -> throw IllegalArgumentException("provide either a slug or a url")
        }

        val found = postService.findBySlug(slugToFind) ?: return null
        return when {
            found.deleted && found.isPublicContent -> PostLookupResult.Gone(
                slug = found.slug,
                url = runCatching { urlService.generatePostUrl(found) }.getOrNull(),
                published = Mf2Graphql.firstText(found.post, "published"),
            )
            found.publiclyReachable -> PostLookupResult.Found(found)
            else -> null
        }
    }
}

sealed interface PostLookupResult {
    data class Found(val post: Post) : PostLookupResult
    data class Gone(val slug: String, val url: String?, val published: String?) : PostLookupResult
}
