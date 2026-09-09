package dev.jacobandersen.bastion.micropub.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "post_syndications")
class PostSyndicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "post_id", nullable = false)
    var postId: UUID,
    @Column(name = "target_uid", nullable = false)
    var targetUid: String,
    @Column(name = "syndicated_url", nullable = true)
    var syndicatedUrl: String? = null,
    @Column(name = "created_at_utc", nullable = false)
    var createdAtUtc: Instant = Instant.now(),
) {
    constructor(postId: UUID, targetUid: String) : this(
        id = null,
        postId = postId,
        targetUid = targetUid,
    )
}
