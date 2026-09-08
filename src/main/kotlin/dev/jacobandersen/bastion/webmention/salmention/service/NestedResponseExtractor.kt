package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.microformats2.firstText

/**
 * Pulls nested responses out of a parsed source document. Nested h-entry/h-cite
 * objects can live either as bare children of a parent microformat or as the
 * value of a property (e.g. a `comment`), so both locations are walked. Only
 * responses that carry a `u-url` are returned - that URL is the response's own
 * permalink and the dedupe key used when ingesting. Object-valued properties
 * that name the entry's own relations or context (its reply/like/repost/
 * bookmark/quotation targets and syndication copies) are skipped, because they
 * reference other posts rather than responses nested in the document.
 *
 * When the received source's own URL is supplied, top-level (sibling-of-entry)
 * h-entry/h-cite page elements are also collected as nested responses when they
 * carry an explicit reply/comment relation (`u-in-reply-to`/`u-comment-of`)
 * pointing back at that URL. A comment thread rendered outside the received
 * entry's own element - the common layout where a webmention/comment widget
 * sits next to `<article>` - then surfaces replies without ingesting unrelated
 * page content.
 */
object NestedResponseExtractor {
    private val RESPONSE_TYPES = setOf("h-entry", "h-cite")
    private val CONTEXT_PROPERTIES =
        setOf("in-reply-to", "comment-of", "repost-of", "like-of", "bookmark-of", "quotation-of", "syndication")
    private val REPLY_RELATIONS = setOf("in-reply-to", "comment-of")

    fun extract(
        parseResult: Mf2ParseResult,
        sourceUrl: String? = null,
    ): List<NestedResponse> {
        val responses = mutableListOf<NestedResponse>()
        parseResult.items.forEach { item ->
            if (sourceUrl != null && isSiblingReplyTo(item, sourceUrl)) {
                collect(item, responses)
            } else {
                walk(item, responses)
            }
        }
        return responses.distinctBy { it.responseUrl }
    }

    fun extract(obj: Mf2Object): List<NestedResponse> {
        val responses = mutableListOf<NestedResponse>()
        walk(obj, responses)
        return responses
    }

    private fun isSiblingReplyTo(
        item: Mf2Object,
        sourceUrl: String,
    ): Boolean =
        item.type.any { it in RESPONSE_TYPES } &&
            item.firstText("url") != null &&
            replyTargets(item).any { it == sourceUrl }

    private fun replyTargets(obj: Mf2Object): List<String> {
        val targets = mutableListOf<String>()
        REPLY_RELATIONS.forEach { property ->
            obj.getProperty(property).forEach { value ->
                when (value) {
                    is Mf2Value.String -> targets += value.value
                    is Mf2Value.Object -> value.value.firstText("url")?.let { targets += it }
                    else -> Unit
                }
            }
        }
        return targets
    }

    private fun walk(
        obj: Mf2Object,
        into: MutableList<NestedResponse>,
    ) {
        obj.children?.forEach { child -> collect(child, into) }
        obj.properties.filterKeys { it !in CONTEXT_PROPERTIES }.forEach { (_, values) ->
            values.forEach { value ->
                if (value is Mf2Value.Object) collect(value.value, into)
            }
        }
    }

    private fun collect(
        obj: Mf2Object,
        into: MutableList<NestedResponse>,
    ) {
        if (obj.type.any { it in RESPONSE_TYPES }) {
            obj.firstText("url")?.let { url -> into.add(NestedResponse(url, obj)) }
        }
        walk(obj, into)
    }
}
