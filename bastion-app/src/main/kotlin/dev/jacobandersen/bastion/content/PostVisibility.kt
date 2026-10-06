package dev.jacobandersen.bastion.content

import java.util.Locale

enum class PostVisibility {
    PUBLIC,
    UNLISTED,
    PRIVATE,
    UNKNOWN,
    ;

    fun canGetByUrl(): Boolean = this == PUBLIC || this == UNLISTED

    companion object {
        fun fromString(str: String): PostVisibility =
            when (str.lowercase(Locale.getDefault())) {
                "public" -> PUBLIC
                "private" -> PRIVATE
                "unlisted" -> UNLISTED
                else -> UNKNOWN
            }
    }
}
