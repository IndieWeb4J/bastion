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
 * permalink and the dedupe key used when ingesting.
 */
object NestedResponseExtractor {
    private val RESPONSE_TYPES = setOf("h-entry", "h-cite")

    fun extract(parseResult: Mf2ParseResult): List<NestedResponse> = parseResult.items.flatMap { extract(it) }.distinctBy { it.responseUrl }

    fun extract(obj: Mf2Object): List<NestedResponse> {
        val responses = mutableListOf<NestedResponse>()
        walk(obj, responses)
        return responses
    }

    private fun walk(
        obj: Mf2Object,
        into: MutableList<NestedResponse>,
    ) {
        obj.children?.forEach { child -> collect(child, into) }
        obj.properties.values.flatten().forEach { value ->
            if (value is Mf2Value.Object) collect(value.value, into)
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
