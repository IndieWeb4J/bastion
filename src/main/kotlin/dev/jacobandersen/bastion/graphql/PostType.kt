package dev.jacobandersen.bastion.graphql

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
