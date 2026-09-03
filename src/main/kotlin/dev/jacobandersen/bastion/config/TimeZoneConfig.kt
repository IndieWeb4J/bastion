package dev.jacobandersen.bastion.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.ZoneId

@Configuration
class TimeZoneConfig(
    @Value($$"${bastion.timezone}")
    val tz: String
) {
    @Bean
    fun bastionTimeZone(): ZoneId {
        return ZoneId.of(tz)
    }
}