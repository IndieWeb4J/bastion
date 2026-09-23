package dev.jacobandersen.bastion.micropub.type

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** Post types that can be requested, mirroring the stored `subtype` values. */
enum class PostType {
    NOTE,
    ARTICLE,
    REPLY,
    REPOST,
    LIKE,
    VIDEO,
    PHOTO,
    RSVP,
}

fun PostType.subtype(): String = name.lowercase()

@Component
class StringToPostTypeConverter : Converter<String, PostType> {
    override fun convert(source: String): PostType =
        try {
            PostType.valueOf(source.uppercase())
        } catch (_: IllegalArgumentException) {
            throw RuntimeException("unknown post type: $source")
        }
}
