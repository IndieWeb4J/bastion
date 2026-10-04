package dev.jacobandersen.bastion.micropub.data.entity

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Generated
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.generator.EventType
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
) {
    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "type", nullable = false, insertable = false, updatable = false)
    var type: String? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "subtype", nullable = true, insertable = false, updatable = false)
    var subtype: String? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "categories", insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    var categories: Array<String>? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "created_at_utc", nullable = false, insertable = false, updatable = false)
    var createdAtUtc: Instant? = null

    @Generated(event = [EventType.INSERT, EventType.UPDATE])
    @Column(name = "updated_at_utc", nullable = false, insertable = false, updatable = false)
    var updatedAtUtc: Instant? = null

    constructor(
        slug: String,
        status: PostStatus,
        visibility: PostVisibility,
        deleted: Boolean = false,
        post: Mf2Object,
    ) : this(
        null,
        slug,
        status,
        visibility,
        deleted,
        post,
    )

    /**
     * Convert this PostEntity to the Post domain object.
     * This will apply a non-null assertion on managed fields.
     * Caller must ensure the entity has already been persisted
     * at call-time.
     *
     * @return Post the post domain object
     */
    fun toDomain(): Post =
        Post(
            id = requireNotNull(id),
            slug = slug,
            status = status,
            visibility = visibility,
            deleted = deleted,
            type = requireNotNull(type),
            subtype = subtype,
            post = post,
        )
}
