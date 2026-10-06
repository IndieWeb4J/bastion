package dev.jacobandersen.bastion.micropub.data.entity

import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.mf24j.Mf2Object
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "posts")
class PostEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false, unique = true)
    var slug: String,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    var status: PostStatus,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    var visibility: PostVisibility,
    @Column(nullable = false)
    var deleted: Boolean = false,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var post: Mf2Object,
    // Application-managed derived columns (formerly JSONB generated columns and
    // PL/pgSQL post_type_discovery); populated from PostTypeDiscovery on save.
    @Column(name = "h", nullable = false)
    var h: String = "h-entry",
    @Column(name = "type", nullable = true)
    var type: String? = null,
    @Column(name = "categories", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    var categories: Array<String> = emptyArray(),
    @Column(name = "created_at_utc", nullable = false)
    var createdAtUtc: Instant = Instant.now(),
    @Column(name = "updated_at_utc", nullable = false)
    var updatedAtUtc: Instant = Instant.now(),
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) {
    /**
     * Convert this PostEntity to the Post domain object. Caller must ensure the
     * entity has already been persisted at call-time.
     */
    fun toDomain(): Post =
        Post(
            id = requireNotNull(id),
            slug = slug,
            status = status,
            visibility = visibility,
            deleted = deleted,
            h = h,
            type = type,
            version = version,
            post = post,
        )
}
