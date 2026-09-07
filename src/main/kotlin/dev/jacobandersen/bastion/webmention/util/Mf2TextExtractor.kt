package dev.jacobandersen.bastion.webmention.util

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.microformats2.htmlOrNull
import dev.jacobandersen.bastion.microformats2.plainTextOrNull

/**
 * Extracts the URLs a microformat references as text, so webmentions can be
 * discovered from a post's content. Only the text-bearing properties of the
 * known vocabularies are inspected.
 */
internal object Mf2TextExtractor {
    private val textPropertiesByType: Map<String, List<String>> = mapOf(
        "h-entry" to listOf(
            "content",
            "summary",
            "in-reply-to",
            "like-of",
            "repost-of",
            "bookmark-of",
            "listen-of",
            "watch-of",
            "read-of",
            "translation-of",
            "checkin",
            "review-of",
        ),
        "h-cite" to listOf(
            "url",
            "content",
        ),
    )

    fun extractText(obj: Mf2Object): List<String> {
        val properties = textPropertiesByType[obj.primaryType()] ?: return emptyList()
        return properties
            .flatMap { key -> obj[key] }
            .flatMap(::extractText)
            .filter { it.isNotBlank() }
    }

    private fun extractText(value: Mf2Value): List<String> {
        return buildList {
            value.plainTextOrNull?.let(::add)
            value.htmlOrNull?.let(::add)
            if (value is Mf2Value.Object) addAll(extractText(value.value))
        }
    }
}
