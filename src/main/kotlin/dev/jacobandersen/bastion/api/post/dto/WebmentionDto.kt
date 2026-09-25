package dev.jacobandersen.bastion.api.post.dto

import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmention
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction

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
        fun from(entity: ReceivedWebmention): WebmentionDto =
            WebmentionDto(
                sourceUrl = entity.sourceUrl,
                targetUrl = entity.targetUrl,
                interaction = entity.interaction ?: WebmentionInteraction.MENTION,
                authorName = entity.authorName,
                authorUrl = entity.authorUrl,
                authorPhoto = entity.authorPhoto,
                contentText = entity.contentText,
                contentHtml = entity.contentHtml,
                firstSeenAt = entity.firstSeenAt.toString(),
                verifiedAt = requireNotNull(entity.verifiedAt) { "verified webmention ${entity.id} missing verifiedAt" }.toString(),
            )
    }
}
