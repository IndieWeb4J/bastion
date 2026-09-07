package dev.jacobandersen.bastion.microformats2

import org.jsoup.nodes.Element

/**
 * Accumulates a single microformat object while its root element is being
 * parsed: the property values and children in document order, plus the
 * bookkeeping needed for implied properties and for adopting a missing date
 * across `dt-*` properties of the same microformat.
 */
internal class Mf2MicroformatBuilder(
    val classicRoots: Set<String>,
    private val resolver: (String) -> String?,
) {
    data class SeenProperty(
        val prefix: Char,
        val name: String,
    )

    data class DtEntry(
        val name: String,
        val result: DtResult,
    )

    val properties = linkedMapOf<String, MutableList<Mf2Value>>()
    val children = mutableListOf<Mf2Object>()
    private val seen = mutableListOf<SeenProperty>()
    private val dtEntries = mutableListOf<DtEntry>()

    fun markPropertySeen(pc: ParsedPropertyClass) {
        seen.add(SeenProperty(pc.prefix, pc.name))
    }

    fun addValue(
        name: String,
        value: Mf2Value,
    ) {
        properties.getOrPut(name) { mutableListOf() }.add(value)
    }

    fun attachObject(
        pc: ParsedPropertyClass,
        child: Mf2Object,
    ) {
        markPropertySeen(pc)
        addValue(pc.name, Mf2Value.Object(child))
    }

    fun recordDt(
        name: String,
        result: DtResult,
    ) {
        dtEntries.add(DtEntry(name, result))
    }

    fun finalizeDt() {
        if (dtEntries.isEmpty()) return

        val finals = arrayOfNulls<String>(dtEntries.size)
        var lastDate: String? = null
        val pending = mutableListOf<Int>()

        for ((index, entry) in dtEntries.withIndex()) {
            val result = entry.result
            when {
                result.date != null -> {
                    lastDate = result.date
                    for (p in pending) {
                        finals[p] = "${result.date} ${dtEntries[p].result.value}"
                    }
                    pending.clear()
                    finals[index] = result.value
                }

                result.hasTime -> {
                    if (lastDate != null) {
                        finals[index] = "$lastDate ${result.value}"
                    } else {
                        pending.add(index)
                    }
                }

                else -> {
                    finals[index] = result.value
                }
            }
        }
        for (p in pending) {
            finals[p] = dtEntries[p].result.value
        }

        for ((index, entry) in dtEntries.withIndex()) {
            val value = finals[index] ?: continue
            if (value.isNotBlank()) {
                addValue(entry.name, Mf2Value.String(value))
            }
        }
    }

    fun finalizeImplied(root: Element) {
        if (classicRoots.isNotEmpty()) return

        val hasName = seen.any { it.name == "name" }
        val otherPOrE = seen.any { (it.prefix == 'p' || it.prefix == 'e') && it.name != "name" }
        val hasPhoto = seen.any { it.name == "photo" }
        val hasUrl = seen.any { it.name == "url" }
        val otherU = seen.any { it.prefix == 'u' && it.name != "photo" }
        val nested = children.isNotEmpty() || properties.values.flatten().any { it is Mf2Value.Object }

        Mf2ImpliedProperties.name(root, hasName, otherPOrE, nested, resolver)?.let { value ->
            if (value.isNotBlank()) addValue("name", Mf2Value.String(value))
        }
        Mf2ImpliedProperties.url(root, hasUrl, otherU, nested, resolver)?.let { value ->
            if (value.isNotBlank()) addValue("url", Mf2Value.String(value))
        }
        Mf2ImpliedProperties.photo(root, hasPhoto, otherU, nested, resolver)?.let { value ->
            addValue("photo", value)
        }
    }
}
