package dev.jacobandersen.bastion.api.post

import dev.jacobandersen.bastion.api.dto.Pagination
import dev.jacobandersen.bastion.api.post.dto.FeedResponse
import dev.jacobandersen.bastion.api.post.dto.PostGoneResponse
import dev.jacobandersen.bastion.api.post.dto.PostLookupResult
import dev.jacobandersen.bastion.api.post.dto.PostResponse
import dev.jacobandersen.bastion.api.post.dto.SyndicationDto
import dev.jacobandersen.bastion.api.post.dto.WebmentionCounts
import dev.jacobandersen.bastion.api.post.dto.WebmentionDto
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.syndication.SyndicationConfig
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostTagFilter
import dev.jacobandersen.bastion.post.PostTypesRegistry
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.mf24j.firstText
import dev.jacobandersen.mf24j.htmls
import dev.jacobandersen.mf24j.texts
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
    private val syndicationService: PostSyndicationService,
    private val syndicationConfig: SyndicationConfig,
    private val postTypesRegistry: PostTypesRegistry,
) {
    @GetMapping
    fun feed(
        @RequestParam(required = false) h: List<String>?,
        @RequestParam(required = false) type: List<String>?,
        @RequestParam(required = false) tag: List<String>? = null,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Int?,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) day: Int?,
    ): FeedResponse {
        val parsedHs = parseHs(h)
        val parsedTypes = parseTypes(type)
        val parsedTags = parseTags(tag)
        val posts = queryService.feed(parsedHs, parsedTypes, limit, offset, year, month, day, parsedTags)
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
                ResponseEntity.ok(toResponse(post, counts, webmentions, syndicationsFor(post.id)))
            }
        }

    private fun toResponse(
        post: Post,
        counts: WebmentionCounts,
        webmentions: List<WebmentionDto>?,
        syndications: List<SyndicationDto>? = null,
    ): PostResponse =
        PostResponse(
            id = post.id.toString(),
            slug = post.slug,
            url = requireNotNull(runCatching { urlService.generatePostUrl(post) }.getOrNull()) { "post ${post.id} missing url" },
            h = post.h,
            type = post.type,
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
            syndications = syndications,
        )

    private fun syndicationsFor(postId: UUID): List<SyndicationDto> {
        val order = syndicationConfig.targets.mapIndexed { index, target -> target.uid to index }.toMap()
        return syndicationService
            .findByPostId(postId)
            .mapNotNull { record ->
                val url = record.syndicatedUrl ?: return@mapNotNull null
                val target = syndicationConfig.targetByUid(record.targetUid) ?: return@mapNotNull null
                SyndicationDto(uid = target.uid, name = target.name, url = url) to
                    (order[target.uid] ?: Int.MAX_VALUE)
            }.sortedBy { it.second }
            .map { it.first }
    }

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

    private fun parseHs(raw: List<String>?): List<PostMf2Type>? =
        parseCsv(raw) {
            val normalized = it.lowercase().let { value -> if (value.startsWith("h-")) value else "h-$value" }
            try {
                PostMf2Type.valueOf(normalized.uppercase().replace('-', '_'))
            } catch (_: IllegalArgumentException) {
                throw RuntimeException("unknown h type: $it")
            }
        }

    private fun parseTypes(raw: List<String>?): List<String>? =
        parseCsv(raw) {
            val value = it.lowercase()
            if (!postTypesRegistry.isKnownType(value)) {
                throw RuntimeException("unknown post type: $it")
            }
            value
        }

    private fun parseTags(raw: List<String>?): PostTagFilter? = PostTagFilter.parse(raw)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (ex.message ?: "bad request")))

    @ExceptionHandler(RuntimeException::class)
    fun handleUnknownType(ex: RuntimeException): ResponseEntity<Map<String, String>> {
        val message = ex.message ?: "bad request"
        return if (message.startsWith("unknown h type") ||
            message.startsWith("unknown post type")
        ) {
            ResponseEntity.badRequest().body(mapOf("error" to message))
        } else {
            throw ex
        }
    }
}
