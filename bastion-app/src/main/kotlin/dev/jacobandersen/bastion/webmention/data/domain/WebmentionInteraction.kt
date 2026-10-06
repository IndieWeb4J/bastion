package dev.jacobandersen.bastion.webmention.data.domain

/**
 * The kind of interaction a received webmention represents, detected from the
 * source document's microformats (or rel attributes).
 */
enum class WebmentionInteraction {
    REPLY,
    LIKE,
    REPOST,
    BOOKMARK,
    RSVP,
    MENTION,
}
