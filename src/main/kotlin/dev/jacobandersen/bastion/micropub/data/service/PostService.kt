package dev.jacobandersen.bastion.micropub.data.service
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.type.mf2.Mf2Object
import dev.jacobandersen.bastion.micropub.data.repository.PostRepository
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.mf2.Mf2Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService(
    val repository: PostRepository,
) {
    fun create(slug: String, status: PostStatus, visibility: PostVisibility, deleted: Boolean, post: Mf2Object): Post {
        return repository.saveAndFlush(PostEntity(slug, status, visibility, deleted, post)).toDomain()
    }

    fun findBySlug(slug: String): Post? {
        return repository.findBySlug(slug)?.toDomain()
    }

    @Transactional(readOnly = true)
    fun doesExistBySlug(slug: String): Boolean {
        return repository.existsBySlug(slug)
    }

    @Transactional(readOnly = true)
    fun deduplicateSlug(baseSlug: String, excludingSlug: String? = null): String {
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
    fun updatePost(post: Post, modified: Mf2Object): Post {
        val ent = post.toEntity()
        ent.post = modified

        val saved = repository.saveAndFlush(ent)

        return saved.toDomain()
    }

    fun findPublicPosts(limit: Int, offset: Int, requestedProperties: Array<String>? = null): List<Post> {
        val pageRequest = PageRequest.of(
            offset / limit,
            limit,
            Sort.by("createdAt").descending()
        )

        val results = repository.findByStatusAndVisibility(
            PostStatus.PUBLISHED,
            PostVisibility.PUBLIC,
            pageRequest
        )

        return results.content.map { enrichPostFields(it.toDomain(), requestedProperties) }
    }

    fun enrichPostFields(post: Post, requestedProperties: Array<String>? = null ): Post {
        val mf2 = post.post

        mf2.setProperty("published", Mf2Value.String(post.createdAt.toString()))
        mf2.setProperty("updated", Mf2Value.String(post.updatedAt.toString()))

        val filteredProperties = mf2.properties.filter { requestedProperties?.contains(it.key) ?: true }
        val filteredMf2 = mf2.copy(properties = filteredProperties.toMutableMap())

        return post.copy(post = filteredMf2)
    }
}