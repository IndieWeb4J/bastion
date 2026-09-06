package dev.jacobandersen.bastion.microformats2

import org.jsoup.nodes.Element
import tools.jackson.databind.node.JsonNodeFactory

/**
 * Implied `name`, `photo` and `url` property parsing per the microformats2
 * parsing specification. Only applied to explicit microformats2 `h-*` roots
 * (never to backward-compatibility roots), and only when the conditions about
 * existing explicit properties and nested microformats are met.
 */
internal object Mf2ImpliedProperties {

    private val H_ROOT = Regex("^h-[a-z0-9]+(?:-[a-z0-9]+)*$")
    private val TEXT_TAGS = setOf("script", "style", "template")

    fun name(el: Element, explicitName: Boolean, otherPOrE: Boolean, hasNested: Boolean, resolver: (String) -> String?): String? {
        if (explicitName || otherPOrE || hasNested) return null

        if ((el.tagName() == "img" || el.tagName() == "area") && el.hasAttr("alt") && el.attr("alt").isNotBlank()) {
            return el.attr("alt").trim()
        }
        if (el.tagName() == "abbr" && el.hasAttr("title") && el.attr("title").isNotBlank()) {
            return el.attr("title").trim()
        }

        val children = el.children()
        onlyChild(children)?.let { child ->
            if (!hasRootClass(child)) {
                val deeper = onlyChild(child.children())
                val matched = imageAlt(child) ?: abbrTitle(child)
                if (matched != null) return matched
                if (deeper != null && !hasRootClass(deeper)) {
                    imageAlt(deeper)?.let { return it }
                    abbrTitle(deeper)?.let { return it }
                }
            }
        }

        return nameText(el, resolver)
    }

    fun photo(el: Element, explicitPhoto: Boolean, otherU: Boolean, hasNested: Boolean, resolver: (String) -> String?): Mf2Value? {
        if (explicitPhoto || otherU || hasNested) return null

        if (el.tagName() == "img" && el.hasAttr("src")) {
            return imgValue(el, resolver)
        }
        if (el.tagName() == "object" && el.hasAttr("data")) {
            return urlValue(el.attr("data"), resolver)
        }

        val children = el.children()
        val directImg = soleTag(children, "img")?.takeIf { it.hasAttr("src") && !hasRootClass(it) }
        directImg?.let { return imgValue(it, resolver) }

        val directObject = soleTag(children, "object")?.takeIf { it.hasAttr("data") && !hasRootClass(it) }
        directObject?.let { return urlValue(it.attr("data"), resolver) }

        onlyChild(children)?.let { child ->
            if (!hasRootClass(child)) {
                soleTag(child.children(), "img")
                    ?.takeIf { it.hasAttr("src") && !hasRootClass(it) }
                    ?.let { return imgValue(it, resolver) }
                soleTag(child.children(), "object")
                    ?.takeIf { it.hasAttr("data") && !hasRootClass(it) }
                    ?.let { return urlValue(it.attr("data"), resolver) }
            }
        }
        return null
    }

    fun url(el: Element, explicitUrl: Boolean, otherU: Boolean, hasNested: Boolean, resolver: (String) -> String?): String? {
        if (explicitUrl || otherU || hasNested) return null

        if ((el.tagName() == "a" || el.tagName() == "area") && el.hasAttr("href")) {
            return resolve(el.attr("href"), resolver)
        }

        val children = el.children()
        val directA = soleTag(children, "a")?.takeIf { it.hasAttr("href") && !hasRootClass(it) }
        directA?.let { return resolve(it.attr("href"), resolver) }
        val directArea = soleTag(children, "area")?.takeIf { it.hasAttr("href") && !hasRootClass(it) }
        directArea?.let { return resolve(it.attr("href"), resolver) }

        onlyChild(children)?.let { child ->
            if (!hasRootClass(child)) {
                soleTag(child.children(), "a")
                    ?.takeIf { it.hasAttr("href") && !hasRootClass(it) }
                    ?.let { return resolve(it.attr("href"), resolver) }
                soleTag(child.children(), "area")
                    ?.takeIf { it.hasAttr("href") && !hasRootClass(it) }
                    ?.let { return resolve(it.attr("href"), resolver) }
            }
        }
        return null
    }

    private fun onlyChild(children: org.jsoup.select.Elements): Element? {
        return if (children.size == 1) children.first() else null
    }

    /** The single element of the given tag among children, or null if not unique. */
    private fun soleTag(children: org.jsoup.select.Elements, tag: String): Element? {
        val matches = children.filter { it.tagName() == tag }
        return if (matches.size == 1) matches.first() else null
    }

    private fun imageAlt(el: Element): String? {
        return if ((el.tagName() == "img" || el.tagName() == "area") && el.hasAttr("alt") && el.attr("alt").isNotBlank()) {
            el.attr("alt").trim()
        } else {
            null
        }
    }

    private fun abbrTitle(el: Element): String? {
        return if (el.tagName() == "abbr" && el.hasAttr("title") && el.attr("title").isNotBlank()) {
            el.attr("title").trim()
        } else {
            null
        }
    }

    private fun imgValue(el: Element, resolver: (String) -> String?): Mf2Value {
        val src = el.attr("src")
        return if (el.hasAttr("alt")) {
            val node = JsonNodeFactory.instance.objectNode()
            node.put("value", resolve(src, resolver) ?: src)
            node.put("alt", el.attr("alt"))
            Mf2Value.Json(node)
        } else {
            Mf2Value.String(resolve(src, resolver) ?: src)
        }
    }

    private fun urlValue(raw: String, resolver: (String) -> String?): Mf2Value {
        return Mf2Value.String(resolve(raw, resolver) ?: raw)
    }

    private fun resolve(raw: String, resolver: (String) -> String?): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return resolver(trimmed)
    }

    private fun hasRootClass(el: Element): Boolean {
        return el.classNames().any { H_ROOT.matches(it) }
    }

    /** Text for an implied name: like p- text but images are replaced only by alt. */
    private fun nameText(el: Element, resolver: (String) -> String?): String {
        val sb = StringBuilder()
        collectNameText(el, sb)
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun collectNameText(el: Element, sb: StringBuilder) {
        for (node in el.childNodes()) {
            when (node) {
                is org.jsoup.nodes.TextNode -> sb.append(node.text())
                is Element -> {
                    when (node.tagName()) {
                        in TEXT_TAGS -> Unit
                        "img" -> if (node.hasAttr("alt") && node.attr("alt").isNotBlank()) {
                            sb.append(' ').append(node.attr("alt")).append(' ')
                        }
                        else -> collectNameText(node, sb)
                    }
                }
            }
        }
    }
}
