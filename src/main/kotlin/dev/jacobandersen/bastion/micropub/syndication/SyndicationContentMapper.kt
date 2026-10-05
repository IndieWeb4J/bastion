package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.micropub.data.domain.Post

/**
 * Builds the downstream copy of a post for syndication targets. The copy is an
 * excerpt plus a permalink, never the full content: notes become
 * `{excerpt}\n\n{url}`, while articles (`type == "article"`) lead with the
 * title as `{name}: {excerpt}\n\n{url}`.
 *
 * The [maxGraphemes] budget covers the total (title + excerpt + link), counted
 * in grapheme clusters via [Graphemes] so emoji-heavy posts stay within limits
 * like Bluesky's 300. The permalink is never dropped or truncated; when the
 * title and link alone exhaust the budget the excerpt is omitted first.
 */
object SyndicationContentMapper {
    fun build(
        post: Post,
        canonicalUrl: String,
        maxGraphemes: Int,
    ): Mf2Object {
        val text = excerptText(post, canonicalUrl, maxGraphemes)

        var mapped = post.post.setProperty("content", Mf2Value.String(text))
        if (post.post.hasProperty("summary")) {
            mapped = mapped.setProperty("summary", Mf2Value.String(text))
        }
        return mapped.setProperty("url", Mf2Value.String(canonicalUrl))
    }

    /** Whether the syndicated text leads with the post title (article posts). */
    fun includesTitle(post: Post): Boolean = post.type.equals("article", ignoreCase = true) && !post.post.firstText("name").isNullOrBlank()

    /** The exact downstream text for a post: excerpt plus permalink within [maxGraphemes]. */
    fun excerptText(
        post: Post,
        canonicalUrl: String,
        maxGraphemes: Int,
    ): String {
        val max = maxGraphemes.coerceAtLeast(1)
        val linkSuffix = "\n\n$canonicalUrl"
        val content = normalizedOrNull(post.post.firstText("content"))
        val name = normalizedOrNull(post.post.firstText("name"))

        return if (includesTitle(post) && name != null) {
            val prefix = "$name: "
            val excerptAllowance = max - Graphemes.count(prefix) - Graphemes.count(linkSuffix)
            if (content != null && excerptAllowance >= MIN_EXCERPT_GRAPHEMES) {
                prefix + truncate(content, excerptAllowance) + linkSuffix
            } else {
                titleOnlyFallback(name, linkSuffix, canonicalUrl, max)
            }
        } else {
            val source = content ?: name
            val excerptAllowance = max - Graphemes.count(linkSuffix)
            if (source != null && excerptAllowance >= MIN_EXCERPT_GRAPHEMES) {
                truncate(source, excerptAllowance) + linkSuffix
            } else {
                canonicalUrl
            }
        }
    }

    private fun titleOnlyFallback(
        name: String,
        linkSuffix: String,
        canonicalUrl: String,
        max: Int,
    ): String {
        val titleAllowance = max - Graphemes.count(linkSuffix)
        return if (titleAllowance >= MIN_TITLE_GRAPHEMES) {
            truncate(name, titleAllowance) + linkSuffix
        } else {
            canonicalUrl
        }
    }

    private fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private fun normalizedOrNull(text: String?): String? = text?.let(::normalize)?.takeIf { it.isNotBlank() }

    private fun truncate(
        text: String,
        allowance: Int,
    ): String {
        if (allowance <= 0) return ""
        if (Graphemes.count(text) <= allowance) return text
        val roomForText = (allowance - 1).coerceAtLeast(0)
        val cut = Graphemes.take(text, roomForText).trimEnd()
        val lastSpace = cut.lastIndexOf(' ')
        val head =
            if (lastSpace >= 0 && Graphemes.count(cut.substring(0, lastSpace)) >= MIN_WORD_KEEP_GRAPHEMES) {
                cut.substring(0, lastSpace).trimEnd()
            } else {
                cut
            }
        return head + "…"
    }

    private const val MIN_EXCERPT_GRAPHEMES = 20
    private const val MIN_TITLE_GRAPHEMES = 10
    private const val MIN_WORD_KEEP_GRAPHEMES = 20
}
