package dev.jacobandersen.bastion.micropub.data.domain

import dev.jacobandersen.bastion.micropub.data.entity.PostEntity
import dev.jacobandersen.bastion.micropub.type.mf2.Mf2Object
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

data class Post(
    val id: UUID,
    val slug: String,
    val status: PostStatus,
    val visibility: PostVisibility,
    val deleted: Boolean = false,
    val type: String,
    val subtype: String? = null,
    val post: Mf2Object,
    val createdAt: LocalDateTime,
    val createdAtUtc: Instant,
    val updatedAt: LocalDateTime,
    val updatedAtUtc: Instant
) {
    /**
     * Map this domain Post to a PostEntity for persisting or updating.
     * `type` and `subtype` are database-generated (`generated always as ... stored`)
     * columns, so they are intentionally not set here - Postgres recomputes them
     * from the `post` JSON on INSERT/UPDATE and Hibernate refetches them after flush.
     * Timestamps are carried over so `createdAt` survives a persist; the
     * DateTrackingListener still bumps `updatedAt` on write.
     */
    fun toEntity(): PostEntity {
        return PostEntity(id = id, slug = slug, status = status, visibility = visibility, deleted = deleted, post = post).also {
            it.createdAt = createdAt
            it.createdAtUtc = createdAtUtc
            it.updatedAt = updatedAt
            it.updatedAtUtc = updatedAtUtc
        }
    }
}