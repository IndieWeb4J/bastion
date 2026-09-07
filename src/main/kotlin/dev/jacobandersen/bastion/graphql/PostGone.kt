package dev.jacobandersen.bastion.graphql

/** Tombstone-safe view of a post that was publicly reachable and is now deleted. */
data class PostGone(
    val slug: String,
    val url: String?,
    val published: String?,
)
