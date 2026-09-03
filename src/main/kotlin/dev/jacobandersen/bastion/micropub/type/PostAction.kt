package dev.jacobandersen.bastion.micropub.type

enum class PostAction {
    CREATE,
    UPDATE,
    DELETE,
    UNDELETE;

    companion object {
        fun fromString(value: String?): PostAction? {
            if (value.isNullOrBlank()) return null
            return entries.find { it.name.equals(value, true) }
        }
    }
}