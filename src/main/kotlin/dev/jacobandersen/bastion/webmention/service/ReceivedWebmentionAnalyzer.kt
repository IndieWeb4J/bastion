package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.microformats2.firstText
import dev.jacobandersen.bastion.microformats2.htmls
import dev.jacobandersen.bastion.microformats2.texts
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.BOOKMARK
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.LIKE
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.REPLY
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.REPOST
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.RSVP

/**
 * Analyzes a parsed source document for webmention purposes: picks the primary
 * object (the first h-entry in document order), classifies the interaction from
 * its `rsvp`/`in-reply-to`/`like-of`/`repost-of`/`bookmark-of` properties
 * (falling back to document-level `rel` attributes when the object declares
 * none) and extracts author/content data.
 */
object ReceivedWebmentionAnalyzer {
    private val interactionProperties: List<Pair<String, WebmentionInteraction>> =
        listOf(
            "rsvp" to RSVP,
            "in-reply-to" to REPLY,
            "like-of" to LIKE,
            "repost-of" to REPOST,
            "bookmark-of" to BOOKMARK,
        )

    private val interactionRels: List<Pair<String, WebmentionInteraction>> =
        listOf(
            "in-reply-to" to REPLY,
            "like-of" to LIKE,
            "repost-of" to REPOST,
            "bookmark-of" to BOOKMARK,
        )

    fun analyze(parseResult: Mf2ParseResult): ReceivedWebmentionAnalysis {
        val primary = primaryObject(parseResult)
        val interaction =
            primary?.let(::classifyProperties)
                ?: classifyRels(parseResult)
                ?: MENTION

        if (primary == null) {
            return ReceivedWebmentionAnalysis(interaction = interaction, primary = null)
        }

        val author = extractAuthor(primary)
        val content = extractContent(primary)

        return ReceivedWebmentionAnalysis(
            interaction = interaction,
            primary = primary,
            authorName = author?.first,
            authorUrl = author?.second,
            authorPhoto = author?.third,
            contentText = content.first,
            contentHtml = content.second,
        )
    }

    /**
     * The primary object of the source: the first h-entry encountered walking
     * the top-level items and their nested children in document order. Falls
     * back to the first item of any kind.
     */
    private fun primaryObject(parseResult: Mf2ParseResult): Mf2Object? {
        for (item in parseResult.items) {
            val entry = findEntry(item)
            if (entry != null) return entry
        }
        return parseResult.items.firstOrNull()
    }

    private fun findEntry(obj: Mf2Object): Mf2Object? {
        if ("h-entry" in obj.type) return obj
        obj.children?.forEach { child ->
            findEntry(child)?.let { return it }
        }
        return null
    }

    private fun classifyProperties(entry: Mf2Object): WebmentionInteraction? {
        for ((property, interaction) in interactionProperties) {
            if (entry.hasProperty(property)) {
                return interaction
            }
        }
        return null
    }

    private fun classifyRels(parseResult: Mf2ParseResult): WebmentionInteraction? {
        for ((rel, interaction) in interactionRels) {
            if (parseResult.rels.containsKey(rel)) {
                return interaction
            }
        }
        return null
    }

    private fun extractAuthor(entry: Mf2Object): Triple<String?, String?, String?>? {
        val authorValue = entry.getProperty("author").firstOrNull() ?: return null
        return when (authorValue) {
            is Mf2Value.Object -> {
                Triple(
                    authorValue.value.firstText("name"),
                    authorValue.value.firstText("url"),
                    authorValue.value.firstText("photo"),
                )
            }

            // A plain string author is treated as the author's URL.
            is Mf2Value.String -> {
                Triple(null, authorValue.value, null)
            }

            else -> {
                null
            }
        }
    }

    private fun extractContent(entry: Mf2Object): Pair<List<String>, List<String>> {
        val contentTexts = entry.texts("content")
        val contentHtmls = entry.htmls("content")
        if (contentTexts.isNotEmpty() || contentHtmls.isNotEmpty()) {
            return Pair(contentTexts, contentHtmls)
        }
        val summaryTexts = entry.texts("summary")
        return Pair(summaryTexts, emptyList())
    }
}
