package dev.jacobandersen.bastion.micropub.type

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** Microformats2 `h-*` types stored in `posts.type`. */
enum class PostMf2Type {
    H_ENTRY,
    H_CARD,
    H_FEED,
    H_EVENT,
    H_CITE,
    H_REVIEW,
    H_PRODUCT,
    H_ITEM,
    H_RECIPE,
}

fun PostMf2Type.mf2Type(): String = name.lowercase().replace('_', '-')

@Component
class StringToPostMf2TypeConverter : Converter<String, PostMf2Type> {
    override fun convert(source: String): PostMf2Type =
        try {
            PostMf2Type.valueOf(source.uppercase().replace('-', '_'))
        } catch (_: IllegalArgumentException) {
            throw RuntimeException("unknown mf2 type: $source")
        }
}
