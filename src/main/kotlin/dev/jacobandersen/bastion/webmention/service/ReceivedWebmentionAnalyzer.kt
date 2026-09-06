package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.BOOKMARK
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.LIKE
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.REPLY
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.REPOST

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

/**
 * Analyzes a parsed source document for webmention purposes: picks the primary
 * object (the first h-entry in document order), classifies the interaction from
 * its `in-reply-to`/`like-of`/`repost-of`/`bookmark-of` properties (falling
 * back to document-level `rel` attributes when the object declares none) and
 * extracts author/content data.
 */
object ReceivedWebmentionAnalyzer {

    private val interactionProperties: List<Pair<String, WebmentionInteraction>> = listOf(
        "in-reply-to" to REPLY,
        "like-of" to LIKE,
        "repost-of" to REPOST,
        "bookmark-of" to BOOKMARK,
    )

    private val interactionRels: List<Pair<String, WebmentionInteraction>> = listOf(
        "in-reply-to" to REPLY,
        "like-of" to LIKE,
        "repost-of" to REPOST,
        "bookmark-of" to BOOKMARK,
    )

    fun analyze(parseResult: Mf2ParseResult): ReceivedWebmentionAnalysis {
        val primary = primaryObject(parseResult)
        val interaction = primary?.let { classifyProperties(it) }
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
            contentText = content?.first,
            contentHtml = content?.second,
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
                val card = authorValue.value
                Triple(
                    card.firstText("name"),
                    card.firstUrl("url"),
                    card.firstUrl("photo"),
                )
            }
            is Mf2Value.String -> Triple(null, authorValue.value, null)
            else -> null
        }
    }

    private fun extractContent(entry: Mf2Object): Pair<String?, String?>? {
        val contentValue = entry.getProperty("content").firstOrNull()
        if (contentValue != null) {
            return when (contentValue) {
                is Mf2Value.String -> Pair(contentValue.value, null)
                is Mf2Value.Json -> Pair(
                    contentValue.value.get("value")?.asText(),
                    contentValue.value.get("html")?.asText(),
                )
                else -> null
            }
        }
        val summary = entry.getProperty("summary").firstOrNull() as? Mf2Value.String
        return summary?.let { Pair(it.value, null) }
    }

    private fun Mf2Object.firstText(key: String): String? {
        return getProperty(key).mapNotNull { value ->
            when (value) {
                is Mf2Value.String -> value.value
                is Mf2Value.Json -> value.value.get("value")?.asText()
                else -> null
            }
        }.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun Mf2Object.firstUrl(key: String): String? {
        return getProperty(key).mapNotNull { value ->
            when (value) {
                is Mf2Value.String -> value.value
                is Mf2Value.Json -> value.value.get("value")?.asText()
                else -> null
            }
        }.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
