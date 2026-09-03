package dev.jacobandersen.bastion.micropub.type

import java.util.Locale

enum class PostStatus {
    PUBLISHED,
    DRAFT,
    UNKNOWN;

    companion object {
        fun fromString(str: String): PostStatus {
            return when (str.lowercase(Locale.getDefault())) {
                "published" -> PUBLISHED
                "draft" -> DRAFT
                else -> UNKNOWN
            }
        }
    }
}