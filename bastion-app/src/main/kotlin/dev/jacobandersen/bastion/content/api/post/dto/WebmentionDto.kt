package dev.jacobandersen.bastion.content.api.post.dto

import dev.jacobandersen.bastion.content.projection.ProjectedWebmentionEntity
import dev.jacobandersen.beacon.WebmentionInteraction

data class WebmentionDto(
    val sourceUrl: String,
    val targetUrl: String,
    val interaction: WebmentionInteraction,
    val authorName: String?,
    val authorUrl: String?,
    val authorPhoto: String?,
    val contentText: List<String>,
    val contentHtml: List<String>,
    val firstSeenAt: String,
    val verifiedAt: String,
) {
    companion object {
        fun from(entity: ProjectedWebmentionEntity): WebmentionDto =
            WebmentionDto(
                sourceUrl = entity.sourceUrl,
                targetUrl = entity.targetUrl,
                interaction = entity.interaction ?: WebmentionInteraction.MENTION,
                authorName = entity.authorName,
                authorUrl = entity.authorUrl,
                authorPhoto = entity.authorPhoto,
                contentText = entity.contentText ?: emptyList(),
                contentHtml = entity.contentHtml ?: emptyList(),
                firstSeenAt = entity.firstSeenAt.toString(),
                verifiedAt = requireNotNull(entity.verifiedAt) { "projected webmention ${entity.id} missing verifiedAt" }.toString(),
            )
    }
}
