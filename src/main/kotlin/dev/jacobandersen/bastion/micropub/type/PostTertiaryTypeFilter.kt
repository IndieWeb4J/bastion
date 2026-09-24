package dev.jacobandersen.bastion.micropub.type

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/**
 * Filter type for the feed `tertiaryType` query parameter. Extends the stored
 * `tertiary_type` values with a `NONE` sentinel meaning `tertiary_type IS NULL`
 * (plain notes where `subtype = 'note'` but no tertiary classification).
 */
enum class PostTertiaryTypeFilter {
    BOOKMARK,
    CHECKIN,
    MOOD,
    NONE,
}

fun PostTertiaryTypeFilter.tertiaryType(): String? =
    when (this) {
        PostTertiaryTypeFilter.NONE -> null
        else -> name.lowercase()
    }

@Component
class StringToPostTertiaryTypeFilterConverter : Converter<String, PostTertiaryTypeFilter> {
    override fun convert(source: String): PostTertiaryTypeFilter =
        try {
            PostTertiaryTypeFilter.valueOf(source.uppercase())
        } catch (_: IllegalArgumentException) {
            throw RuntimeException("unknown tertiary type: $source")
        }
}
