package dev.jacobandersen.bastion.webmention.data.domain

import dev.jacobandersen.bastion.microformats2.Mf2Object

/**
 * The result of analyzing a received webmention's source document: the
 * detected interaction type plus the normalized author and content extracted
 * from its primary microformats object.
 */
data class ReceivedWebmentionAnalysis(
    val interaction: WebmentionInteraction,
    val primary: Mf2Object?,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val authorPhoto: String? = null,
    val contentText: String? = null,
    val contentHtml: String? = null,
)
