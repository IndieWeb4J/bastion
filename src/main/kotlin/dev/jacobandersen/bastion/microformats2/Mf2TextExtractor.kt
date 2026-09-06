package dev.jacobandersen.bastion.microformats2

import tools.jackson.databind.JsonNode

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
            "review-of"
        ),
        "h-cite" to listOf(
            "url",
            "content"
        ),
    )

    fun extractText(obj: Mf2Object): List<String> {
        val properties = textPropertiesByType[obj.primaryType()] ?: return emptyList()
        return properties
            .flatMap { key -> obj[key] }
            .flatMap { value -> extractText(value) }
            .filter { it.isNotBlank() }
    }

    private fun extractText(value: Mf2Value): List<String> {
        return when (value) {
            is Mf2Value.String -> listOf(value.value)
            is Mf2Value.Json -> extractJsonText(value.value)
            is Mf2Value.Object -> extractText(value.value)
            is Mf2Value.Boolean, is Mf2Value.Number, is Mf2Value.Float -> emptyList()
        }
    }

    private fun extractJsonText(node: JsonNode): List<String> {
        val result = mutableListOf<String>()
        val value = node.get("value")
        if (value != null && value.isString) {
            result.add(value.asString())
        }
        val html = node.get("html")
        if (html != null && html.isString) {
            result.add(html.asString())
        }
        return result
    }
}
