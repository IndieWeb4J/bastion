package dev.jacobandersen.bastion.api.post.dto

import dev.jacobandersen.bastion.api.dto.Pagination

data class FeedResponse(
    val items: List<PostResponse>,
    val pagination: Pagination,
)
