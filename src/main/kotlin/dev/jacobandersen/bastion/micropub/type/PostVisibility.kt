package dev.jacobandersen.bastion.micropub.type

import java.util.Locale

enum class PostVisibility {
    PUBLIC,
    UNLISTED,
    PRIVATE,
    UNKNOWN;

    fun canGetByUrl(): Boolean {
        return this == PUBLIC || this == UNLISTED
    }

    companion object {
        fun fromString(str: String): PostVisibility {
            return when (str.lowercase(Locale.getDefault())) {
                "public" -> PUBLIC
                "private" -> PRIVATE
                "unlisted" -> UNLISTED
                else -> UNKNOWN
            }
        }
    }
}