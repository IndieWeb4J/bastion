package dev.jacobandersen.bastion.microformats2

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tools.jackson.databind.node.ObjectNode

/**
 * One microformats parsing run over a single document. Owns the session-local
 * state (`items`, `rels`, `rel-urls`), resolves the effective document base
 * (honoring a leading `<base href>`), and walks the tree per the parsing
 * algorithm. Constructed per parse call so the parser implementation stays
 * stateless.
 */
internal class Mf2ParseSession(
    html: String,
    baseUrl: String,
) {
    private val document: Document = Jsoup.parse(html)
    private val baseUrl: String = effectiveBase(document, baseUrl)
    private val resolver: (String) -> String? = { raw -> Mf2UrlResolver.resolve(baseUrl, raw) }

    private val items = mutableListOf<Mf2Object>()
    private val rels = linkedMapOf<String, MutableList<String>>()
    private val relUrls = linkedMapOf<String, Mf2RelUrl>()

    fun parse(): Mf2ParseResult {
        collectTopLevelItems(document)
        collectRels(document)
        val finalRels = rels.mapValues { it.value.toList() }
        return Mf2ParseResult(items = items, rels = finalRels, relUrls = relUrls)
    }

    private fun effectiveBase(
        document: Document,
        baseUrl: String,
    ): String {
        val baseEl = document.selectFirst("base[href]") ?: return baseUrl
        val href = baseEl.attr("href").trim()
        if (href.isEmpty()) return baseUrl
        return Mf2UrlResolver.resolve(baseUrl, href) ?: baseUrl
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

        val types =
            if (classicRoots.isEmpty()) {
                Mf2PropertyParser.mf2RootClasses(root.classNames()).distinct().sorted()
            } else {
                Mf2Backcompat.classicRootTypes(classicRoots)
            }

        val children = builder.children.takeIf { it.isNotEmpty() }?.toList()
        val finalProperties = builder.properties.mapValuesTo(linkedMapOf()) { it.value.toList() }
        return Mf2Object(types, finalProperties, children)
    }

    /**
     * Scan the children of the microformat root element for property elements
     * and nested microformats, per the parsing algorithm.
     */
    private fun scan(
        el: Element,
        builder: Mf2MicroformatBuilder,
    ) {
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

    private fun parseProperty(
        builder: Mf2MicroformatBuilder,
        el: Element,
        pc: ParsedPropertyClass,
    ) {
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

            'd' -> {
                builder.recordDt(pc.name, Mf2PropertyParser.parseDt(el, resolver))
            }

            'e' -> {
                builder.addValue(pc.name, Mf2Value.Json(Mf2PropertyParser.parseE(el, resolver)))
            }
        }
    }

    private fun propertyClassesFor(
        el: Element,
        classicRoots: Set<String>,
    ): List<ParsedPropertyClass> =
        if (classicRoots.isEmpty()) {
            Mf2PropertyParser.mf2PropertyClasses(el.classNames())
        } else {
            val inScope = Mf2Backcompat.classicPropertyClasses(classicRoots)
            el.classNames().mapNotNull { inScope[it] }
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
            val url = resolver(href) ?: continue

            val relValues = relAttr.split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
            for (relValue in relValues) {
                val list = rels.getOrPut(relValue) { mutableListOf() }
                if (url !in list) list.add(url)
            }

            val existing = relUrls[url]
            if (existing == null) {
                relUrls[url] =
                    Mf2RelUrl(
                        rels = relValues.sorted(),
                        hreflang = el.attr("hreflang").takeIf { it.isNotBlank() },
                        media = el.attr("media").takeIf { it.isNotBlank() },
                        title = el.attr("title").takeIf { it.isNotBlank() },
                        type = el.attr("type").takeIf { it.isNotBlank() },
                        text =
                            el
                                .text()
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .takeIf { it.isNotBlank() },
                    )
            } else {
                val merged = (existing.rels + relValues).distinct().sorted()
                relUrls[url] = existing.copy(rels = merged)
            }
        }
    }
}
