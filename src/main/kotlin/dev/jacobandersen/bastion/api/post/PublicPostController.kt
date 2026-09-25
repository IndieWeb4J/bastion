package dev.jacobandersen.bastion.api.post

import dev.jacobandersen.bastion.api.dto.Pagination
import dev.jacobandersen.bastion.api.post.dto.FeedResponse
import dev.jacobandersen.bastion.api.post.dto.PostGoneResponse
import dev.jacobandersen.bastion.api.post.dto.PostLookupResult
import dev.jacobandersen.bastion.api.post.dto.PostResponse
import dev.jacobandersen.bastion.api.post.dto.WebmentionCounts
import dev.jacobandersen.bastion.api.post.dto.WebmentionDto
import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.microformats2.htmls
import dev.jacobandersen.bastion.microformats2.texts
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostTagFilter
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
import java.util.UUID

@RestController
@RequestMapping("/api/posts")
class PublicPostController(
    private val queryService: PostQueryService,
    private val urlService: UrlService,
    private val webmentionService: ReceivedWebmentionService,
) {
    @GetMapping
    fun feed(
        @RequestParam(required = false) type: List<String>?,
        @RequestParam(required = false) subtype: List<String>?,
        @RequestParam(required = false) tertiaryType: List<String>?,
        @RequestParam(required = false) tag: List<String>? = null,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) day: Int?,
    ): FeedResponse {
        val parsedTypes = parseMf2Types(type)
        val parsedSubtypes = parseSubtypes(subtype)
        val parsedTertiaryTypes = parseTertiaryTypes(tertiaryType)
        val parsedTags = parseTags(tag)
        val posts = queryService.feed(parsedTypes, parsedSubtypes, parsedTertiaryTypes, limit, offset, year, month, day, parsedTags)
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
                val webmentions =
                    webmentionService.verifiedByPost(post.id).sortedBy { it.firstSeenAt }.map(WebmentionDto.Companion::from)
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

    private fun countsByPost(posts: List<Post>): Map<UUID, WebmentionCounts> {
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

    private fun <T> parseCsv(
        raw: List<String>?,
        convert: (String) -> T,
    ): List<T>? =
        raw
            ?.flatMap { it.split(",") }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map(convert)
            ?.takeIf { it.isNotEmpty() }

    private fun parseMf2Types(raw: List<String>?): List<PostMf2Type>? =
        parseCsv(raw) {
            try {
                PostMf2Type.valueOf(it.uppercase().replace('-', '_'))
            } catch (_: IllegalArgumentException) {
                throw RuntimeException("unknown mf2 type: $it")
            }
        }

    private fun parseSubtypes(raw: List<String>?): List<PostType>? =
        parseCsv(raw) {
            try {
                PostType.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                throw RuntimeException("unknown post type: $it")
            }
        }

    private fun parseTertiaryTypes(raw: List<String>?): List<PostTertiaryTypeFilter>? =
        parseCsv(raw) {
            try {
                PostTertiaryTypeFilter.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                throw RuntimeException("unknown tertiary type: $it")
            }
        }

    private fun parseTags(raw: List<String>?): PostTagFilter? = PostTagFilter.parse(raw)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "bad request")))

    @ExceptionHandler(RuntimeException::class)
    fun handleUnknownType(ex: RuntimeException): ResponseEntity<Map<String, String>> {
        val message = ex.message ?: "bad request"
        return if (message.startsWith("unknown mf2 type") ||
            message.startsWith("unknown post type") ||
            message.startsWith("unknown tertiary type")
        ) {
            ResponseEntity.badRequest().body(mapOf("error" to message))
        } else {
            throw ex
        }
    }
}
