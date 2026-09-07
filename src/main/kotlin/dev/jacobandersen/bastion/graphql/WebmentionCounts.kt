package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction

/** Per-post counts of verified webmentions by interaction type. */
data class WebmentionCounts(
    val total: Int,
    val reply: Int,
    val like: Int,
    val repost: Int,
    val bookmark: Int,
    val rsvp: Int,
    val mention: Int,
) {
    companion object {
        val EMPTY = WebmentionCounts(0, 0, 0, 0, 0, 0, 0)

        fun of(counts: Map<WebmentionInteraction, Int>): WebmentionCounts {
            return WebmentionCounts(
                total = counts.values.sum(),
                reply = counts[WebmentionInteraction.REPLY] ?: 0,
                like = counts[WebmentionInteraction.LIKE] ?: 0,
                repost = counts[WebmentionInteraction.REPOST] ?: 0,
                bookmark = counts[WebmentionInteraction.BOOKMARK] ?: 0,
                rsvp = counts[WebmentionInteraction.RSVP] ?: 0,
                mention = counts[WebmentionInteraction.MENTION] ?: 0,
            )
        }
    }
}
