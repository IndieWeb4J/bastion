package dev.jacobandersen.bastion.content.api.dto

data class Pagination(
    val limit: Int,
    val offset: Int,
    val count: Int,
    val hasMore: Boolean,
)
