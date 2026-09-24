package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.data.repository.PostRepository
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostTertiaryTypeFilter
import dev.jacobandersen.bastion.micropub.type.PostType
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.mf2Type
import dev.jacobandersen.bastion.micropub.type.subtype
import dev.jacobandersen.bastion.micropub.type.tertiaryType
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
        return repository.saveAndFlush(PostEntity(slug, status, visibility, deleted, stamped)).toDomain()
    }

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): Post? = repository.findBySlug(slug)?.toDomain()

    @Transactional(readOnly = true)
    fun findById(id: UUID): Post? = repository.findById(id).orElse(null)?.toDomain()

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

        return repository.saveAndFlush(entity).toDomain()
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
     * restricted by mf2 `type` (h-entry, h-card, ...), `subtype` and
     * `tertiary_type`. Filters within each list are OR, across lists are AND.
     * For `tertiary_type`, the `NONE` sentinel means `tertiary_type IS NULL`.
     */
    @Transactional(readOnly = true)
    fun findFeedPosts(
        mf2Types: Collection<PostMf2Type>?,
        subtypes: Collection<PostType>?,
        tertiaryFilters: Collection<PostTertiaryTypeFilter>?,
        limit: Int,
        offset: Int,
    ): List<Post> = findFeedPosts(mf2Types, subtypes, tertiaryFilters, limit, offset, null, null)

    @Transactional(readOnly = true)
    fun findFeedPosts(
        mf2Types: Collection<PostMf2Type>?,
        subtypes: Collection<PostType>?,
        tertiaryFilters: Collection<PostTertiaryTypeFilter>?,
        limit: Int,
        offset: Int,
        from: Instant?,
        toExclusive: Instant?,
    ): List<Post> {
        require(offset % limit == 0) { "offset must be a multiple of limit" }
        require((from == null) == (toExclusive == null)) { "both from and toExclusive must be provided together" }

        val pageRequest = PageRequest.of(offset / limit, limit, Sort.by("createdAtUtc").descending())

        val mf2TypeStrings = mf2Types?.map { it.mf2Type() }
        val subtypeStrings = subtypes?.map { it.subtype() }
        val includeTertiaryNone = tertiaryFilters?.any { it == PostTertiaryTypeFilter.NONE } == true
        val tertiaryTypeStrings = tertiaryFilters?.filterNot { it == PostTertiaryTypeFilter.NONE }?.mapNotNull { it.tertiaryType() }

        val spec = buildFeedSpecification(mf2TypeStrings, subtypeStrings, tertiaryTypeStrings, includeTertiaryNone, from, toExclusive)
        return repository.findAll(spec, pageRequest).content.map { it.toDomain() }
    }

    private fun buildFeedSpecification(
        mf2Types: Collection<String>?,
        subtypes: Collection<String>?,
        tertiaryTypes: Collection<String>?,
        includeTertiaryNone: Boolean,
        from: Instant?,
        toExclusive: Instant?,
    ): Specification<PostEntity> =
        Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            predicates += cb.equal(root.get<PostStatus>("status"), PostStatus.PUBLISHED)
            predicates += cb.equal(root.get<PostVisibility>("visibility"), PostVisibility.PUBLIC)
            predicates += cb.isFalse(root.get("deleted"))

            if (!mf2Types.isNullOrEmpty()) {
                predicates += root.get<String>("type").`in`(mf2Types)
            }
            if (!subtypes.isNullOrEmpty()) {
                predicates += root.get<String>("subtype").`in`(subtypes)
            }

            val hasTertiaryValues = !tertiaryTypes.isNullOrEmpty()
            if (hasTertiaryValues || includeTertiaryNone) {
                val tertiaryPath = root.get<String>("tertiaryType")
                val clause =
                    when {
                        hasTertiaryValues && includeTertiaryNone -> {
                            cb.or(tertiaryPath.isNull, tertiaryPath.`in`(tertiaryTypes))
                        }

                        hasTertiaryValues -> {
                            tertiaryPath.`in`(tertiaryTypes)
                        }

                        else -> {
                            tertiaryPath.isNull
                        }
                    }
                predicates += clause
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
