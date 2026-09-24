package dev.jacobandersen.bastion.api

import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostTertiaryTypeFilter
import dev.jacobandersen.bastion.micropub.type.PostType
import dev.jacobandersen.bastion.url.UrlService
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Read queries over published posts for public (unauthenticated) REST use.
 * Lists return only PUBLIC posts; direct lookups additionally allow UNLISTED.
 * PRIVATE and DRAFT posts are never returned here (private is only reachable
 * through the authenticated Micropub source query). A post that was publicly
 * reachable and is now soft-deleted surfaces as [PostLookupResult.Gone].
 */
@Service
class PostQueryService(
    private val postService: PostService,
    private val urlService: UrlService,
    private val zone: ZoneId,
) {
    fun feed(
        type: List<PostMf2Type>?,
        subtype: List<PostType>?,
        tertiaryType: List<PostTertiaryTypeFilter>?,
        limitArg: Int?,
        offsetArg: Int?,
        year: Int? = null,
        month: Int? = null,
        day: Int? = null,
    ): List<Post> {
        val limit = limitArg ?: 10
        val offset = offsetArg ?: 0

        require(limit in 1..PostService.MAX_PAGE_SIZE) { "limit must be an integer between 1 and ${PostService.MAX_PAGE_SIZE}" }
        require(offset >= 0) { "offset must be a non-negative integer" }
        require(offset % limit == 0) { "offset must be a multiple of limit ($offset % $limit != 0)" }

        val range = resolveDateRange(year, month, day)

        return if (range == null) {
            postService.findFeedPosts(type, subtype, tertiaryType, limit, offset)
        } else {
            postService.findFeedPosts(
                type,
                subtype,
                tertiaryType,
                limit,
                offset,
                range.first,
                range.second,
            )
        }
    }

    private fun resolveDateRange(
        year: Int?,
        month: Int?,
        day: Int?,
    ): Pair<java.time.Instant, java.time.Instant>? {
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
     * or was never publicly reachable (draft/private); [PostLookupResult.Gone]
     * when the post existed publicly and has since been deleted.
     */
    fun postBySlug(slug: String): PostLookupResult? {
        val found = postService.findBySlug(slug) ?: return null
        return toLookupResult(found)
    }

    fun postByUrl(url: String): PostLookupResult? {
        val slug =
            urlService.extractPostSlug(url)
                ?: throw IllegalArgumentException("url is not a URL on this Bastion instance")
        val found = postService.findBySlug(slug) ?: return null
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

sealed interface PostLookupResult {
    data class Found(
        val post: Post,
    ) : PostLookupResult

    data class Gone(
        val slug: String,
        val url: String,
        val published: String,
    ) : PostLookupResult
}
