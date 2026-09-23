package dev.jacobandersen.bastion.micropub.type

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** Tertiary types discovered from h-entry notes, mirroring stored `tertiary_type` values. */
enum class PostTertiaryType {
    BOOKMARK,
    CHECKIN,
    MOOD,
}

fun PostTertiaryType.tertiaryType(): String = name.lowercase()

@Component
class StringToPostTertiaryTypeConverter : Converter<String, PostTertiaryType> {
    override fun convert(source: String): PostTertiaryType =
        try {
            PostTertiaryType.valueOf(source.uppercase())
        } catch (_: IllegalArgumentException) {
            throw RuntimeException("unknown tertiary type: $source")
        }
}
