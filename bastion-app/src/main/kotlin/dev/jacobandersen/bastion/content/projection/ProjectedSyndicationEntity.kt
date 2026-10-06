package dev.jacobandersen.bastion.content.projection

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A syndicated copy projected from Conduit's `syndication.*` events. Idempotent
 * upsert keyed by (post_id, target_uid); no cross-service foreign key.
 */
@Entity
@Table(name = "projected_syndications")
class ProjectedSyndicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "post_id", nullable = false)
    var postId: UUID,
    @Column(name = "target_uid", nullable = false)
    var targetUid: String,
    @Column(nullable = true)
    var name: String? = null,
    @Column(nullable = false)
    var url: String,
    @Column(name = "updated_at_utc", nullable = false)
    var updatedAtUtc: Instant,
)
