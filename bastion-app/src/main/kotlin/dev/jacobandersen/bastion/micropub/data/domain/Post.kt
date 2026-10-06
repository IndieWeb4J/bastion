package dev.jacobandersen.bastion.micropub.data.domain

import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import java.time.OffsetDateTime
import java.util.UUID

data class Post(
    val id: UUID,
    val slug: String,
    val status: PostStatus,
    val visibility: PostVisibility,
    val deleted: Boolean = false,
    val h: String,
    val type: String? = null,
    val version: Long = 0,
    val post: Mf2Object,
) {
    val publishedAt: OffsetDateTime?
        get() =
            (post.getFirstProperty("published") as? Mf2Value.String)
                ?.value
                ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    /**
     * Whether the post is published content that could be shown publicly,
     * regardless of a soft-delete tombstone (used to decide dispatch behavior
     * when toggling delete/undelete).
     */
    val isPublicContent: Boolean
        get() = status == PostStatus.PUBLISHED && visibility.canGetByUrl()

    /** Whether the post is currently publicly reachable (not deleted). */
    val publiclyReachable: Boolean
        get() = !deleted && isPublicContent

    /**
     * Map this domain Post to a PostEntity for persisting a new post.
     * `h`, `type`, `created_at_utc` and `updated_at_utc` are
     * database-generated columns, so they are intentionally not set here -
     * Postgres recomputes them from the `post` JSON on INSERT and Hibernate
     * refetches them after flush. Updates go through the managed entity
     * instead of building a detached one.
     */
    fun toEntity(): PostEntity = PostEntity(id = id, slug = slug, status = status, visibility = visibility, deleted = deleted, post = post)
}
