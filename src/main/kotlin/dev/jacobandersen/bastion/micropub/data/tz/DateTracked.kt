package dev.jacobandersen.bastion.micropub.data.tz

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@MappedSuperclass
@EntityListeners(DateTrackingListener::class)
abstract class DateTracked(
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    var createdAtUtc: Instant? = null,

    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var updatedAtUtc: Instant? = null,
) {
    fun updateTimes(zone: ZoneId) {
        val now = Instant.now()

        if (createdAtUtc == null) {
            createdAtUtc = now
            createdAt = LocalDateTime.ofInstant(now, zone)
        }

        updatedAtUtc = now
        updatedAt = LocalDateTime.ofInstant(now, zone)
    }
}