package dev.jacobandersen.bastion.micropub.data.tz

import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.springframework.stereotype.Component
import java.time.ZoneId

@Component
class DateTrackingListener(
    val timezone: ZoneId,
) {
    @PrePersist
    fun onPrePersist(ent: DateTracked) {
        ent.updateTimes(timezone)
    }

    @PreUpdate
    fun onPreUpdate(ent: DateTracked) {
        ent.updateTimes(timezone)
    }
}