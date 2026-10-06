package dev.jacobandersen.bastion.api.post.dto

import dev.jacobandersen.bastion.content.Post

sealed interface PostLookupResult {
    data class Found(
        val post: Post,
    ) : PostLookupResult

    data class Gone(
        val slug: String,
        val url: String,
        val published: String,
    ) : PostLookupResult
}
