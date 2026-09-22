package dev.jacobandersen.bastion.micropub.data.repository

import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface PostRepository : JpaRepository<PostEntity, UUID> {
    fun existsBySlug(slug: String): Boolean

    fun findBySlug(slug: String): PostEntity?

    fun findByStatusAndVisibilityAndDeletedFalse(
        status: PostStatus,
        visibility: PostVisibility,
        page: Pageable,
    ): Page<PostEntity>

    fun findByStatusAndVisibilityAndDeletedFalseAndSubtypeIn(
        status: PostStatus,
        visibility: PostVisibility,
        subtypes: Collection<String>,
        page: Pageable,
    ): Page<PostEntity>

    fun findByStatusAndVisibilityAndDeletedFalseAndCreatedAtUtcGreaterThanEqualAndCreatedAtUtcLessThan(
        status: PostStatus,
        visibility: PostVisibility,
        from: Instant,
        to: Instant,
        page: Pageable,
    ): Page<PostEntity>

    fun findByStatusAndVisibilityAndDeletedFalseAndSubtypeInAndCreatedAtUtcGreaterThanEqualAndCreatedAtUtcLessThan(
        status: PostStatus,
        visibility: PostVisibility,
        subtypes: Collection<String>,
        from: Instant,
        to: Instant,
        page: Pageable,
    ): Page<PostEntity>
}
