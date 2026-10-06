package dev.jacobandersen.bastion.content.api.tag.dto

import dev.jacobandersen.bastion.content.api.dto.Pagination

data class TagListResponse(
    val tags: List<TagResponse>,
    val pagination: Pagination,
)
