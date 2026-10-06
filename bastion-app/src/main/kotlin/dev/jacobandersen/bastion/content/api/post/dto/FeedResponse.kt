package dev.jacobandersen.bastion.content.api.post.dto

import dev.jacobandersen.bastion.content.api.dto.Pagination

data class FeedResponse(
    val items: List<PostResponse>,
    val pagination: Pagination,
)
