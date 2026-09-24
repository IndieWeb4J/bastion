package dev.jacobandersen.bastion.api

import dev.jacobandersen.bastion.api.dto.FeedResponse
import dev.jacobandersen.bastion.api.dto.Pagination
import dev.jacobandersen.bastion.api.dto.PostGoneResponse
import dev.jacobandersen.bastion.api.dto.PostResponse
import dev.jacobandersen.bastion.api.dto.WebmentionCounts
import dev.jacobandersen.bastion.api.dto.WebmentionDto
import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.microformats2.htmls
import dev.jacobandersen.bastion.microformats2.texts
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostTertiaryTypeFilter
import dev.jacobandersen.bastion.micropub.type.PostType
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts")
class PublicPostController(
    private val queryService: PostQueryService,
    private val urlService: UrlService,
    private val webmentionService: ReceivedWebmentionService,
) {
    @GetMapping
    fun feed(
        @RequestParam(required = false) type: List<PostMf2Type>?,
        @RequestParam(required = false) subtype: List<PostType>?,
        @RequestParam(required = false) tertiaryType: List<PostTertiaryTypeFilter>?,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) day: Int?,
    ): FeedResponse {
        val posts = queryService.feed(type, subtype, tertiaryType, limit, offset, year, month, day)
        val webmentionCounts = countsByPost(posts)
        val items = posts.map { post -> toResponse(post, webmentionCounts[post.id] ?: WebmentionCounts.EMPTY, webmentions = null) }
        val effectiveLimit = limit ?: 10
        val effectiveOffset = offset ?: 0
        return FeedResponse(
            items = items,
            pagination =
                Pagination(
                    limit = effectiveLimit,
                    offset = effectiveOffset,
                    count = items.size,
                    hasMore = items.size == effectiveLimit,
                ),
        )
    }

    /**
     * Lookup by URL - primary lookup path. Frontend can pass the current page URL
     * to discover the post without parsing slug/date from the path.
     */
    @GetMapping("/lookup")
    fun lookupByUrl(
        @RequestParam url: String,
    ): ResponseEntity<Any> = resolvePost(queryService.postByUrl(url))

    @GetMapping("/{slug}")
    fun bySlug(
        @PathVariable slug: String,
    ): ResponseEntity<Any> = resolvePost(queryService.postBySlug(slug))

    private fun resolvePost(result: PostLookupResult?): ResponseEntity<Any> =
        when (result) {
            null -> {
                ResponseEntity.notFound().build()
            }

            is PostLookupResult.Gone -> {
                ResponseEntity
                    .status(HttpStatus.GONE)
                    .body(PostGoneResponse(result.slug, result.url, result.published))
            }

            is PostLookupResult.Found -> {
                val post = result.post
                val counts = countForPost(post)
                val webmentions = webmentionService.verifiedByPost(post.id).sortedBy { it.firstSeenAt }.map(WebmentionDto::from)
                ResponseEntity.ok(toResponse(post, counts, webmentions))
            }
        }

    private fun toResponse(
        post: Post,
        counts: WebmentionCounts,
        webmentions: List<WebmentionDto>?,
    ): PostResponse =
        PostResponse(
            id = post.id.toString(),
            slug = post.slug,
            url = requireNotNull(runCatching { urlService.generatePostUrl(post) }.getOrNull()) { "post ${post.id} missing url" },
            type = post.type,
            subtype = post.subtype,
            tertiaryType = post.tertiaryType,
            published = requireNotNull(post.post.firstText("published")) { "post ${post.id} missing published" },
            updated = requireNotNull(post.post.firstText("updated")) { "post ${post.id} missing updated" },
            name = post.post.firstText("name"),
            summary = post.post.texts("summary"),
            content = post.post.texts("content"),
            contentHtml = post.post.htmls("content"),
            category = post.post.texts("category"),
            properties = post.post.properties,
            webmentionCounts = counts,
            webmentions = webmentions,
        )

    private fun countForPost(post: Post): WebmentionCounts {
        val byInteraction =
            webmentionService
                .verifiedByPost(post.id)
                .groupingBy { it.interaction ?: MENTION }
                .eachCount()
        return WebmentionCounts.of(byInteraction)
    }

    private fun countsByPost(posts: List<Post>): Map<java.util.UUID, WebmentionCounts> {
        if (posts.isEmpty()) return emptyMap()
        val byPost = webmentionService.verifiedByPostIds(posts.map { it.id }).groupBy { it.postId }
        return posts.associate { post ->
            val counts =
                byPost[post.id]
                    ?.groupingBy { it.interaction ?: MENTION }
                    ?.eachCount()
                    ?: emptyMap()
            post.id to WebmentionCounts.of(counts)
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "bad request")))
}
