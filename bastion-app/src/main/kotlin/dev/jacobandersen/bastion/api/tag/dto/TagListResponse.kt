package dev.jacobandersen.bastion.api.tag.dto

import dev.jacobandersen.bastion.api.dto.Pagination

data class TagListResponse(
    val tags: List<TagResponse>,
    val pagination: Pagination,
)
