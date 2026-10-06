package dev.jacobandersen.bastion.api.post

import dev.jacobandersen.bastion.api.post.dto.PostLookupResult
import dev.jacobandersen.mf24j.firstText
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostTagFilter
import dev.jacobandersen.bastion.url.UrlService
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Read queries over published posts for public (unauthenticated) REST use.
 * Lists return only PUBLIC posts; direct lookups additionally allow UNLISTED.
 * PRIVATE and DRAFT posts are never returned here (private is only reachable
 * through the authenticated Micropub source query). A post that was publicly
 * reachable and is now soft-deleted surfaces as [dev.jacobandersen.bastion.api.post.dto.PostLookupResult.Gone].
 */
@Service
class PostQueryService(
    private val postService: PostService,
    private val urlService: UrlService,
    private val zone: ZoneId,
) {
    fun feed(
        h: List<PostMf2Type>?,
        type: List<String>?,
        limitArg: Int?,
        offsetArg: Int?,
        year: Int? = null,
        month: Int? = null,
        day: Int? = null,
        tagFilter: PostTagFilter? = null,
    ): List<Post> {
        val limit = limitArg ?: 10
        val offset = offsetArg ?: 0

        require(limit in 1..PostService.MAX_PAGE_SIZE) { "limit must be an integer between 1 and ${PostService.MAX_PAGE_SIZE}" }
        require(offset >= 0) { "offset must be a non-negative integer" }
        require(offset % limit == 0) { "offset must be a multiple of limit ($offset % $limit != 0)" }

        val range = resolveDateRange(year, month, day)

        return if (range == null) {
            postService.findFeedPosts(h, type, limit, offset, null, null, tagFilter)
        } else {
            postService.findFeedPosts(
                h,
                type,
                limit,
                offset,
                range.first,
                range.second,
                tagFilter,
            )
        }
    }

    private fun resolveDateRange(
        year: Int?,
        month: Int?,
        day: Int?,
    ): Pair<Instant, Instant>? {
        if (year == null && month == null && day == null) return null

        require(year != null) { "year is required when month or day is provided" }
        require(year in 1..9999) { "year must be between 1 and 9999" }

        if (day != null) {
            require(month != null) { "month is required when day is provided" }
        }

        if (month != null) {
            require(month in 1..12) { "month must be between 1 and 12" }
        }

        if (day != null) {
            val maxDay = YearMonth.of(year, month!!).lengthOfMonth()
            require(day in 1..maxDay) { "day must be between 1 and $maxDay for $year-${month.toString().padStart(2, '0')}" }
        }

        val start =
            when {
                day != null -> ZonedDateTime.of(year, month!!, day, 0, 0, 0, 0, zone)
                month != null -> ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone)
                else -> ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, zone)
            }
        val end =
            when {
                day != null -> start.plusDays(1)
                month != null -> start.plusMonths(1)
                else -> start.plusYears(1)
            }

        return start.toInstant() to end.toInstant()
    }

    /**
     * Resolves a direct post lookup. Returns null when the post does not exist
     * or was never publicly reachable (draft/private); [dev.jacobandersen.bastion.api.post.dto.PostLookupResult.Gone]
     * when the post existed publicly and has since been deleted.
     */
    fun postBySlug(slug: String): PostLookupResult? {
        val found = postService.findBySlug(slug) ?: return null
        return toLookupResult(found)
    }

    fun postByUrl(url: String): PostLookupResult? {
        val ref =
            urlService.extractPostRef(url)
                ?: throw IllegalArgumentException("url is not a URL on this Bastion instance")
        val found = postService.findBySlug(ref.slug) ?: return null

        // Strict date validation: if the path pattern contains date placeholders,
        // the URL must match the post's published date, otherwise 404.
        val pattern = urlService.config.pathPattern
        val publishedAt = found.publishedAt
        if (pattern.contains("{year}") && ref.year != publishedAt?.year) return null
        if (pattern.contains("{month}") && ref.month != publishedAt?.monthValue) return null
        if (pattern.contains("{day}") && ref.day != publishedAt?.dayOfMonth) return null

        return toLookupResult(found)
    }

    private fun toLookupResult(found: Post): PostLookupResult? =
        when {
            found.deleted && found.isPublicContent -> {
                PostLookupResult.Gone(
                    slug = found.slug,
                    url = requireNotNull(runCatching { urlService.generatePostUrl(found) }.getOrNull()) { "post ${found.id} missing url" },
                    published = requireNotNull(found.post.firstText("published")) { "post ${found.id} missing published" },
                )
            }

            found.publiclyReachable -> {
                PostLookupResult.Found(found)
            }

            else -> {
                null
            }
        }
}
