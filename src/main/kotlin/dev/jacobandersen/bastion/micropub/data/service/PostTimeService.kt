package dev.jacobandersen.bastion.micropub.data.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class PostTimeService(
    private val zone: ZoneId,
) {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun nowIso(): String = isoFormatter.format(now())

    fun now(): OffsetDateTime = OffsetDateTime.now(zone).truncatedTo(ChronoUnit.SECONDS)

    fun normalizeOrThrow(raw: String): OffsetDateTime {
        return parse(raw) ?: throw IllegalArgumentException("Invalid timestamp: $raw")
    }

    fun applyCreateTimestamps(post: Mf2Object) {
        val existing = post.getFirstProperty("published")
        if (existing == null) {
            post.addProperty("published", Mf2Value.String(nowIso()))
        } else {
            val raw = existing as? Mf2Value.String
                ?: throw IllegalArgumentException("published must be a string value")
            post.setProperty("published", Mf2Value.String(isoFormatter.format(normalizeOrThrow(raw.value))))
        }
        post.setProperty("updated", Mf2Value.String(nowIso()))
    }

    fun applyUpdateTimestamps(post: Mf2Object) {
        val existing = post.getFirstProperty("published")
        if (existing is Mf2Value.String) {
            post.setProperty("published", Mf2Value.String(isoFormatter.format(normalizeOrThrow(existing.value))))
        }
        post.setProperty("updated", Mf2Value.String(nowIso()))
    }

    private fun parse(raw: String): OffsetDateTime? {
        return runCatching { OffsetDateTime.parse(raw) }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()?.atOffset(ZoneOffset.UTC)
            ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { OffsetDateTime.of(it, offsetFor(it)) }
            ?: runCatching { LocalDate.parse(raw) }.getOrNull()?.atStartOfDay(zone)?.toOffsetDateTime()
    }

    private fun offsetFor(ldt: LocalDateTime): ZoneOffset = zone.rules.getOffset(ldt)
}
