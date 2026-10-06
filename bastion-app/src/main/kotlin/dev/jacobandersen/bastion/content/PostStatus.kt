package dev.jacobandersen.bastion.content

import java.util.Locale

enum class PostStatus {
    PUBLISHED,
    DRAFT,
    UNKNOWN,
    ;

    companion object {
        fun fromString(str: String): PostStatus =
            when (str.lowercase(Locale.getDefault())) {
                "published" -> PUBLISHED
                "draft" -> DRAFT
                else -> UNKNOWN
            }
    }
}
