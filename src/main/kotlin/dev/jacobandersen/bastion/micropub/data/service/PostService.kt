package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.data.repository.PostRepository
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostTagFilter
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.mf2Type
import dev.jacobandersen.bastion.post.PostTypeDiscovery
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.firstText
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PostService(
    val repository: PostRepository,
    private val postTimeService: PostTimeService,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
    }

    @Transactional
    fun create(
        slug: String,
        status: PostStatus,
        visibility: PostVisibility,
        deleted: Boolean,
        post: Mf2Object,
    ): Post {
        val stamped = postTimeService.applyCreateTimestamps(post)
        val entity = PostEntity(slug = slug, status = status, visibility = visibility, deleted = deleted, post = stamped, version = 1)
        applyDerived(entity)
        return repository.saveAndFlush(entity).toDomain()
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): Post? = repository.findBySlug(slug)?.toDomain()

    @Transactional(readOnly = true)
    fun findById(id: UUID): Post? = repository.findById(id).orElse(null)?.toDomain()

    /**
     * Posts changed since an opaque cursor (epoch millis, null = from the
     * beginning), oldest first, paired with their `updated_at_utc` and a cursor
     * for the next page (null when there are no more). Feeds consumer
     * reconciliation.
     */
    @Transactional(readOnly = true)
    fun changedSince(
        cursor: String?,
        limit: Int,
    ): Pair<List<Pair<Post, Instant>>, String?> {
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val from = cursor?.let { runCatching { Instant.ofEpochMilli(it.toLong()) }.getOrNull() } ?: Instant.EPOCH
        val page = repository.findByUpdatedAtUtcAfterOrderByUpdatedAtUtcAsc(from, PageRequest.of(0, size))
        val items = page.content.map { it.toDomain() to it.updatedAtUtc }
        val next =
            if (page.hasNext()) {
                items
                    .lastOrNull()
                    ?.second
                    ?.toEpochMilli()
                    ?.toString()
            } else {
                null
            }
        return items to next
    }

    @Transactional(readOnly = true)
    fun doesExistBySlug(slug: String): Boolean = repository.existsBySlug(slug)

    @Transactional(readOnly = true)
    fun deduplicateSlug(
        baseSlug: String,
        excludingSlug: String? = null,
    ): String {
        if (excludingSlug == baseSlug) return baseSlug

        var candidate = baseSlug
        var count = 2
        while (repository.existsBySlug(candidate)) {
            candidate = "$baseSlug-$count"
            count++
        }
        return candidate
    }

    @Transactional
    fun updatePost(
        post: Post,
        modified: Mf2Object,
    ): Post {
        val stamped = postTimeService.applyUpdateTimestamps(modified)

        val entity =
            repository
                .findById(post.id)
                .orElseThrow { IllegalArgumentException("Post not found for id ${post.id}") }
        entity.slug = post.slug
        entity.status = post.status
        entity.visibility = post.visibility
        entity.deleted = post.deleted
        entity.post = stamped
        entity.version = entity.version + 1
        applyDerived(entity)

        return repository.saveAndFlush(entity).toDomain()
    }

    /**
     * Recompute the application-managed derived columns from the mf2 document:
     * the mf2 primary type (`h`), the discovered post type (`type`), the
     * normalized category list, and the published/updated timestamps. Mirrors
     * the former JSONB generated columns and PL/pgSQL post-type discovery.
     */
    private fun applyDerived(entity: PostEntity) {
        val post = entity.post
        entity.h = PostTypeDiscovery.primaryType(post) ?: "h-entry"
        entity.type = PostTypeDiscovery.discover(post)
        entity.categories = PostTypeDiscovery.categories(post).toTypedArray()
        entity.createdAtUtc =
            post.firstText("published")?.let(postTimeService::normalizeOrThrow)?.toInstant() ?: postTimeService.now().toInstant()
        entity.updatedAtUtc =
            post.firstText("updated")?.let(postTimeService::normalizeOrThrow)?.toInstant() ?: postTimeService.now().toInstant()
    }

    @Transactional(readOnly = true)
    fun findPublicPosts(
        limit: Int,
        offset: Int,
        requestedProperties: Array<String>? = null,
    ): List<Post> {
        require(offset % limit == 0) { "offset must be a multiple of limit" }

        val pageRequest = PageRequest.of(offset / limit, limit, Sort.by("createdAtUtc").descending())

        val results =
            repository.findByStatusAndVisibilityAndDeletedFalse(
                PostStatus.PUBLISHED,
                PostVisibility.PUBLIC,
                pageRequest,
            )

        return results.content.map { filterPostFields(it.toDomain(), requestedProperties) }
    }

    /**
     * Published, public, non-deleted posts for the public feed, optionally
     * restricted by mf2 `type` (h-entry, h-card, ...), `type` and tags.
     * Filters within each list are OR, across lists are AND. For tags, the
     * `none` sentinel means an empty categories array.
     */
    @Transactional(readOnly = true)
    fun findFeedPosts(
        mf2Types: Collection<PostMf2Type>?,
        types: Collection<String>?,
        limit: Int,
        offset: Int,
    ): List<Post> = findFeedPosts(mf2Types, types, limit, offset, null, null, null)

    @Transactional(readOnly = true)
    fun findFeedPosts(
        mf2Types: Collection<PostMf2Type>?,
        types: Collection<String>?,
        limit: Int,
        offset: Int,
        from: Instant?,
        toExclusive: Instant?,
    ): List<Post> = findFeedPosts(mf2Types, types, limit, offset, from, toExclusive, null)

    @Transactional(readOnly = true)
    fun findFeedPosts(
        mf2Types: Collection<PostMf2Type>?,
        types: Collection<String>?,
        limit: Int,
        offset: Int,
        from: Instant?,
        toExclusive: Instant?,
        tagFilter: PostTagFilter?,
    ): List<Post> {
        require(offset % limit == 0) { "offset must be a multiple of limit" }
        require((from == null) == (toExclusive == null)) { "both from and toExclusive must be provided together" }

        val pageRequest = PageRequest.of(offset / limit, limit, Sort.by("createdAtUtc").descending())

        val mf2TypeStrings = mf2Types?.map { it.mf2Type() }

        val spec =
            buildFeedSpecification(
                mf2TypeStrings,
                types,
                from,
                toExclusive,
                tagFilter,
            )
        return repository.findAll(spec, pageRequest).content.map { it.toDomain() }
    }

    private fun buildFeedSpecification(
        mf2Types: Collection<String>?,
        types: Collection<String>?,
        from: Instant?,
        toExclusive: Instant?,
        tagFilter: PostTagFilter? = null,
    ): Specification<PostEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            predicates += cb.equal(root.get<PostStatus>("status"), PostStatus.PUBLISHED)
            predicates += cb.equal(root.get<PostVisibility>("visibility"), PostVisibility.PUBLIC)
            predicates += cb.isFalse(root.get("deleted"))

            if (!mf2Types.isNullOrEmpty()) {
                predicates += root.get<String>("h").`in`(mf2Types)
            }
            if (!types.isNullOrEmpty()) {
                predicates += root.get<String>("type").`in`(types)
            }

            if (tagFilter != null) {
                val categories = root.get<Array<String>>("categories")
                val ordinaryTagPredicate =
                    if (tagFilter.tags.isNotEmpty()) {
                        cb.isTrue(
                            cb.function(
                                "categories_overlap",
                                Boolean::class.java,
                                categories,
                                cb.literal(tagFilter.tags.toTypedArray()),
                            ),
                        )
                    } else {
                        null
                    }
                val untaggedPredicate =
                    if (tagFilter.includeUntagged) {
                        cb.isTrue(
                            cb.function(
                                "categories_empty",
                                Boolean::class.java,
                                categories,
                            ),
                        )
                    } else {
                        null
                    }

                when {
                    untaggedPredicate != null && ordinaryTagPredicate != null -> {
                        predicates += cb.or(untaggedPredicate, ordinaryTagPredicate)
                    }

                    untaggedPredicate != null -> {
                        predicates += untaggedPredicate
                    }

                    ordinaryTagPredicate != null -> {
                        predicates += ordinaryTagPredicate
                    }
                }
            }

            if (from != null && toExclusive != null) {
                predicates += cb.greaterThanOrEqualTo(root.get("createdAtUtc"), from)
                predicates += cb.lessThan(root.get("createdAtUtc"), toExclusive)
            }

            cb.and(*predicates.toTypedArray())
        }

    fun filterPostFields(
        post: Post,
        requestedProperties: Array<String>? = null,
    ): Post {
        if (requestedProperties == null) return post
        val filtered = post.post.properties.filterKeys { it in requestedProperties }
        return post.copy(post = post.post.copy(properties = filtered))
    }
}
