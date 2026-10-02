package dev.jacobandersen.bastion.api.post.dto

import dev.jacobandersen.bastion.microformats2.Mf2Value

data class PostResponse(
    val id: String,
    val slug: String,
    val url: String,
    val type: String,
    val subtype: String?,
    val tertiaryType: String?,
    val published: String,
    val updated: String,
    val name: String?,
    val summary: List<String>,
    val content: List<String>,
    val contentHtml: List<String>,
    val category: List<String>,
    val properties: Map<String, List<Mf2Value>>,
    val webmentionCounts: WebmentionCounts,
    val webmentions: List<WebmentionDto>? = null,
    val syndications: List<SyndicationDto>? = null,
)
