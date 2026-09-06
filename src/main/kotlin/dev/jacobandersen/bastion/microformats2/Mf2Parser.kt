package dev.jacobandersen.bastion.microformats2

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tools.jackson.databind.node.ObjectNode
import java.net.URI

/**
 * A microformats2 parser producing the canonical `{items, rels, rel-urls}`
 * structure, following the algorithm on the microformats2 parsing
 * specification (including the value-class-pattern and backward-compatibility
 * class mappings).
 */
object Mf2Parser {

    fun parse(html: String, baseUrl: String): Mf2ParseResult {
        val document = Jsoup.parse(html)
        val base = effectiveBase(document, baseUrl)
        val resolver: (String) -> String? = { raw -> resolveUrl(base, raw) }
        return Mf2ParserImpl(document, base, resolver).parse()
    }

    private fun effectiveBase(document: Document, baseUrl: String): String {
        val baseEl = document.selectFirst("base[href]") ?: return baseUrl
        val href = baseEl.attr("href").trim()
        if (href.isEmpty()) return baseUrl
        return resolveUrl(baseUrl, href) ?: baseUrl
    }

    internal fun resolveUrl(base: String, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (base.isEmpty()) {
            return if (isAbsolute(trimmed)) trimmed else null
        }
        val parsed = runCatching { URI(base) }.getOrNull() ?: return null
        return runCatching { parsed.resolve(trimmed).toString() }.getOrNull()
    }

    private fun isAbsolute(raw: String): Boolean {
        return raw.contains(":") && runCatching { URI(raw).isAbsolute }.getOrDefault(false)
    }
}

private class Mf2ParserImpl(
    private val document: Document,
    private val baseUrl: String,
    private val resolver: (String) -> String?,
) {
    private val items = mutableListOf<Mf2Object>()
    private val rels = linkedMapOf<String, MutableList<String>>()
    private val relUrls = linkedMapOf<String, Mf2RelUrl>()

    fun parse(): Mf2ParseResult {
        collectTopLevelItems(document)
        collectRels(document)
        val finalRels = rels.mapValues { it.value.toList() }
        return Mf2ParseResult(items = items, rels = finalRels, relUrls = relUrls)
    }

    private fun collectTopLevelItems(root: Element) {
        fun walk(el: Element) {
            for (child in el.children()) {
                if (child.tagName() == "template") continue
                if (isRootElement(child)) {
                    items.add(buildMicroformat(child))
                } else {
                    walk(child)
                }
            }
        }
        walk(root)
    }

    private fun buildMicroformat(root: Element): Mf2Object {
        val classicRoots = classicRootsOf(root)
        val builder = Mf2MicroformatBuilder(classicRoots, resolver)

        scan(root, builder)
        builder.finalizeDt()
        builder.finalizeImplied(root)

        val types = if (classicRoots.isEmpty()) {
            Mf2PropertyParser.mf2RootClasses(root.classNames()).distinct().sorted()
        } else {
            Mf2Backcompat.classicRootTypes(classicRoots)
        }

        val children = builder.children.takeIf { it.isNotEmpty() }
        val finalProperties = builder.properties.mapValuesTo(linkedMapOf()) { it.value.toList() }
        return Mf2Object(types, finalProperties, children)
    }

    private fun scan(el: Element, builder: Mf2MicroformatBuilder) {
        for (child in el.children()) {
            if (child.tagName() == "template") continue

            if (isRootElement(child)) {
                val childObject = buildMicroformat(child)
                val props = propertyClassesFor(child, builder.classicRoots)
                if (props.isEmpty()) {
                    builder.children.add(childObject)
                } else {
                    for (pc in props.distinctBy { it.name }) {
                        builder.attachObject(pc, childObject)
                    }
                }
            } else {
                for (pc in propertyClassesFor(child, builder.classicRoots)) {
                    parseProperty(builder, child, pc)
                }
                scan(child, builder)
            }
        }
    }

    private fun parseProperty(builder: Mf2MicroformatBuilder, el: Element, pc: ParsedPropertyClass) {
        builder.markPropertySeen(pc)
        when (pc.prefix) {
            'p' -> {
                val value = Mf2PropertyParser.parseP(el, resolver)
                if (value.isNotBlank()) {
                    builder.addValue(pc.name, Mf2Value.String(value))
                }
            }
            'u' -> {
                when (val parsed = Mf2PropertyParser.parseU(el, resolver)) {
                    is String -> builder.addValue(pc.name, Mf2Value.String(parsed))
                    is ObjectNode -> builder.addValue(pc.name, Mf2Value.Json(parsed))
                    else -> Unit
                }
            }
            'd' -> builder.recordDt(pc.name, Mf2PropertyParser.parseDt(el, resolver))
            'e' -> builder.addValue(pc.name, Mf2Value.Json(Mf2PropertyParser.parseE(el, resolver)))
        }
    }

    private fun propertyClassesFor(el: Element, classicRoots: Set<String>): List<ParsedPropertyClass> {
        return if (classicRoots.isEmpty()) {
            Mf2PropertyParser.mf2PropertyClasses(el.classNames())
        } else {
            val inScope = Mf2Backcompat.classicPropertyClasses(classicRoots)
            el.classNames().mapNotNull { inScope[it] }
        }
    }

    private fun isRootElement(el: Element): Boolean {
        val classes = el.classNames()
        return Mf2PropertyParser.hasMf2RootClass(classes) || classes.any(Mf2Backcompat::isClassicRoot)
    }

    private fun classicRootsOf(el: Element): Set<String> {
        val classes = el.classNames()
        if (Mf2PropertyParser.hasMf2RootClass(classes)) return emptySet()
        return classes.filter(Mf2Backcompat::isClassicRoot).toSet()
    }

    private fun collectRels(root: Element) {
        for (el in root.select("a[rel], area[rel], link[rel]")) {
            val relAttr = el.attr("rel")
            if (relAttr.isBlank()) continue
            val href = el.attr("href")
            if (href.isBlank()) continue
            val url = Mf2Parser.resolveUrl(baseUrl, href) ?: continue

            val relValues = relAttr.split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
            for (relValue in relValues) {
                val list = rels.getOrPut(relValue) { mutableListOf() }
                if (url !in list) list.add(url)
            }

            val existing = relUrls[url]
            if (existing == null) {
                relUrls[url] = Mf2RelUrl(
                    rels = relValues.sorted(),
                    hreflang = el.attr("hreflang").takeIf { it.isNotBlank() },
                    media = el.attr("media").takeIf { it.isNotBlank() },
                    title = el.attr("title").takeIf { it.isNotBlank() },
                    type = el.attr("type").takeIf { it.isNotBlank() },
                    text = el.text().replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() },
                )
            } else {
                val merged = (existing.rels + relValues).distinct().sorted()
                relUrls[url] = existing.copy(rels = merged)
            }
        }
    }
}

private class Mf2MicroformatBuilder(
    val classicRoots: Set<String>,
    private val resolver: (String) -> String?,
) {
    data class SeenProperty(val prefix: Char, val name: String)

    data class DtEntry(val name: String, val result: DtResult)

    val properties = linkedMapOf<String, MutableList<Mf2Value>>()
    val children = mutableListOf<Mf2Object>()
    private val seen = mutableListOf<SeenProperty>()
    private val dtEntries = mutableListOf<DtEntry>()

    fun markPropertySeen(pc: ParsedPropertyClass) {
        seen.add(SeenProperty(pc.prefix, pc.name))
    }

    fun addValue(name: String, value: Mf2Value) {
        properties.getOrPut(name) { mutableListOf() }.add(value)
    }

    fun attachObject(pc: ParsedPropertyClass, child: Mf2Object) {
        markPropertySeen(pc)
        addValue(pc.name, Mf2Value.Object(child))
    }

    fun recordDt(name: String, result: DtResult) {
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
                else -> finals[index] = result.value
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
