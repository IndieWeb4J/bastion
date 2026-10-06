package dev.jacobandersen.bastion.content.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_outbox")
class ContentOutboxEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var subject: String,
    @Column(name = "partition_key", nullable = true)
    var partitionKey: String? = null,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    var payload: String,
    @Column(name = "created_at_utc", nullable = false)
    var createdAtUtc: Instant = Instant.now(),
    @Column(name = "published_at_utc", nullable = true)
    var publishedAtUtc: Instant? = null,
)
