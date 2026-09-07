package dev.jacobandersen.bastion.micropub.data.service
import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.data.repository.PostRepository
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
     * restricted to the given discovered post types (subtypes).
     */
    @Transactional(readOnly = true)
    fun findFeedPosts(
        subtypes: Collection<String>?,
        limit: Int,
        offset: Int,
    ): List<Post> {
        require(offset % limit == 0) { "offset must be a multiple of limit" }

        val pageRequest = PageRequest.of(offset / limit, limit, Sort.by("createdAtUtc").descending())

        val results =
            if (subtypes.isNullOrEmpty()) {
                repository.findByStatusAndVisibilityAndDeletedFalse(
                    PostStatus.PUBLISHED,
                    PostVisibility.PUBLIC,
                    pageRequest,
                )
            } else {
                repository.findByStatusAndVisibilityAndDeletedFalseAndSubtypeIn(
                    PostStatus.PUBLISHED,
                    PostVisibility.PUBLIC,
                    subtypes,
                    pageRequest,
                )
            }

        return results.content.map { it.toDomain() }
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
