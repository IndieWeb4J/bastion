package dev.jacobandersen.bastion.api.dto

import dev.jacobandersen.bastion.microformats2.Mf2Value

data class PostResponse(
    val id: String,
    val slug: String,
    val url: String?,
    val type: String,
    val subtype: String?,
    val published: String?,
    val updated: String?,
    val name: String?,
    val summary: String?,
    val content: String?,
    val contentHtml: String?,
    val category: List<String>,
    val properties: Map<String, List<Mf2Value>>,
    val webmentionCounts: WebmentionCounts,
    val webmentions: List<WebmentionDto>? = null,
)

data class FeedResponse(
    val items: List<PostResponse>,
    val pagination: Pagination,
)

data class Pagination(
    val limit: Int,
    val offset: Int,
    val count: Int,
    val hasMore: Boolean,
)
