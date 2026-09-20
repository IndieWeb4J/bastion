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

    fun normalizeOrThrow(raw: String): OffsetDateTime = parse(raw) ?: throw IllegalArgumentException("Invalid timestamp: $raw")

    /**
     * Normalizes `published` (or supplies it from now) and stamps `updated`,
     * returning the resulting object.
     */
    fun applyCreateTimestamps(post: Mf2Object): Mf2Object {
        val published =
            when (val existing = post.getFirstProperty("published")) {
                null -> Mf2Value.String(nowIso())
                is Mf2Value.String -> Mf2Value.String(isoFormatter.format(normalizeOrThrow(existing.value)))
                else -> throw IllegalArgumentException("published must be a string value")
            }
        return post
            .setProperty("published", published)
            .setProperty("updated", Mf2Value.String(nowIso()))
    }

    /** Normalizes `published` (when present) and stamps `updated`. */
    fun applyUpdateTimestamps(post: Mf2Object): Mf2Object {
        val withPublished =
            (post.getFirstProperty("published") as? Mf2Value.String)?.let { existing ->
                post.setProperty("published", Mf2Value.String(isoFormatter.format(normalizeOrThrow(existing.value))))
            } ?: post
        return withPublished.setProperty("updated", Mf2Value.String(nowIso()))
    }

    private fun parse(raw: String): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(raw) }.getOrNull()
            ?: runCatching { Instant.parse(raw) }.getOrNull()?.atOffset(ZoneOffset.UTC)
            ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { OffsetDateTime.of(it, offsetFor(it)) }
            ?: runCatching { LocalDate.parse(raw) }.getOrNull()?.atStartOfDay(zone)?.toOffsetDateTime()

    private fun offsetFor(ldt: LocalDateTime): ZoneOffset = zone.rules.getOffset(ldt)
}
