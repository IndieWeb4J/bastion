package dev.jacobandersen.bastion.webmention.data.entity

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionNotification
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionState
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
@Table(name = "webmention_notifications")
class WebmentionNotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false)
    var sourceUrl: String,
    @Column(nullable = false)
    var targetUrl: String,
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    var state: WebmentionState,
    @Column(nullable = false)
    var delivered: Boolean = false,
    @Column(nullable = false)
    var attempts: Int = 0,
    @Column(nullable = true)
    var lastError: String? = null,
    @Column(nullable = true)
    var lastStatusCode: Int? = null,
    @Column(nullable = true)
    var lastAttemptAt: Instant? = null,
    @Column(nullable = true)
    var nextAttemptAt: Instant? = null,
    @Column(nullable = false)
    var createdAtUtc: Instant,
    @Column(nullable = false)
    var updatedAtUtc: Instant,
) {
    fun toDomain(): WebmentionNotification =
        WebmentionNotification(
            id = requireNotNull(this.id),
            sourceUrl = this.sourceUrl,
            targetUrl = this.targetUrl,
            state = this.state,
            delivered = this.delivered,
            attempts = this.attempts,
            lastError = this.lastError,
            lastStatusCode = this.lastStatusCode,
            lastAttemptAt = this.lastAttemptAt,
            nextAttemptAt = this.nextAttemptAt,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
        )
}
