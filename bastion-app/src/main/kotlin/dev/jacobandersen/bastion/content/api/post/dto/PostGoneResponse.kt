package dev.jacobandersen.bastion.content.api.post.dto

/** Tombstone for a post that was publicly reachable and is now deleted. */
data class PostGoneResponse(
    val slug: String,
    val url: String,
    val published: String,
)
