package dev.jacobandersen.bastion.microformats2

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

/**
 * Parsing of individual microformats property values, implementing the
 * microformats2 parsing specification sections for `p-`, `u-`, `dt-` and `e-`
 * properties, including the value-class-pattern and its date/time rules.
 */
internal object Mf2PropertyParser {
    private val PROPERTY_CLASS = Regex("^(p|u|dt|e)-([a-z0-9]+(?:-[a-z0-9]+)*)$")
    private val ROOT_CLASS = Regex("^h-([a-z0-9]+(?:-[a-z0-9]+)*)$")

    private val TAG_A = "a"
    private val TAG_AREA = "area"
    private val TAG_LINK = "link"
    private val TAG_IMG = "img"
    private val TAG_AUDIO = "audio"
    private val TAG_VIDEO = "video"
    private val TAG_SOURCE = "source"
    private val TAG_IFRAME = "iframe"
    private val TAG_OBJECT = "object"
    private val TAG_ABBR = "abbr"
    private val TAG_DATA = "data"
    private val TAG_INPUT = "input"
    private val TAG_TIME = "time"
    private val TAG_INS = "ins"
    private val TAG_DEL = "del"
    private val TAG_SCRIPT = "script"
    private val TAG_STYLE = "style"
    private val TAG_TEMPLATE = "template"

    private val CLASS_VALUE = "value"
    private val CLASS_VALUE_TITLE = "value-title"

    private val DATE_ONLY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val DATE_ORDINAL = Regex("^\\d{4}-\\d{3}$")
    private val TIMEZONE = Regex("^(Z|[+-]\\d{2}:?\\d{2}|[+-]\\d{2})$")
    private val TIME_WITH_TZ =
        Regex(
            "^([01]?\\d|2[0-4])(?::([0-5]?\\d))?(?::([0-5]?\\d|60))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?\\s*(Z|[+-]\\d{2}:?\\d{2}|[+-]\\d{2})?$",
            RegexOption.IGNORE_CASE,
        )
    private val DATE_TIME =
        Regex("^(\\d{4}-\\d{2}-\\d{2})[T ](.+)$")

    fun mf2PropertyClasses(classNames: Set<String>): List<ParsedPropertyClass> =
        classNames
            .asSequence()
            .mapNotNull { className ->
                val match = PROPERTY_CLASS.matchEntire(className)
                if (match == null) {
                    null
                } else {
                    val prefix = match.groupValues[1]
                    ParsedPropertyClass(if (prefix == "dt") 'd' else prefix[0], match.groupValues[2])
                }
            }.toList()

    fun mf2RootClasses(classNames: Set<String>): List<String> =
        classNames
            .asSequence()
            .mapNotNull { ROOT_CLASS.matchEntire(it)?.groupValues?.get(1) }
            .map { "h-$it" }
            .toList()

    fun hasMf2RootClass(classNames: Set<String>): Boolean = classNames.any { ROOT_CLASS.matches(it) }

    /**
     * The text content of the element per the parsing specification: drops
     * nested script/style/template, replaces nested imgs with their alt (or
     * resolved src) surrounded by spaces, collapses whitespace and trims.
     */
    fun textContent(
        el: Element,
        resolver: (String) -> String?,
    ): String {
        val sb = StringBuilder()
        collectText(el, sb, resolver)
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun collectText(
        el: Element,
        sb: StringBuilder,
        resolver: (String) -> String?,
    ) {
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> {
                    sb.append(node.text())
                }

                is Element -> {
                    when (val tag = node.tagName()) {
                        TAG_SCRIPT, TAG_STYLE, TAG_TEMPLATE -> {
                            Unit
                        }

                        TAG_IMG -> {
                            val alt = node.attr("alt")
                            val replacement =
                                if (alt.isNotBlank()) {
                                    alt
                                } else {
                                    resolver(node.attr("src")) ?: node.attr("src")
                                }
                            sb.append(' ').append(replacement).append(' ')
                        }

                        else -> {
                            collectText(node, sb, resolver)
                        }
                    }
                }
            }
        }
    }

    /** The serialized inner HTML of the element with surrounding whitespace trimmed. */
    fun innerHtml(el: Element): String = el.html().trim()

    /**
     * Parse a `p-` property value.
     */
    fun parseP(
        el: Element,
        resolver: (String) -> String?,
    ): String {
        valueClassText(el, resolver)?.let { return it }

        return when (el.tagName()) {
            TAG_ABBR, TAG_LINK -> {
                if (el.hasAttr("title")) el.attr("title").trim() else textContent(el, resolver)
            }

            TAG_DATA, TAG_INPUT -> {
                if (el.hasAttr("value")) el.attr("value").trim() else textContent(el, resolver)
            }

            TAG_IMG, TAG_AREA -> {
                if (el.hasAttr("alt")) el.attr("alt").trim() else textContent(el, resolver)
            }

            else -> {
                textContent(el, resolver)
            }
        }
    }

    /**
     * Parse a `u-` property value, normalized to an absolute URL. An `img`
     * with an `alt` attribute yields a `{value, alt}` object. Returns null when
     * the element has no usable value.
     */
    fun parseU(
        el: Element,
        resolver: (String) -> String?,
    ): Any? {
        val tag = el.tagName()
        val raw: String =
            when (tag) {
                TAG_A, TAG_AREA, TAG_LINK -> {
                    if (el.hasAttr("href")) el.attr("href") else ""
                }

                TAG_IMG -> {
                    return parseImg(el, resolver)
                }

                TAG_AUDIO, TAG_VIDEO, TAG_SOURCE, TAG_IFRAME -> {
                    if (el.hasAttr("src")) {
                        el.attr("src")
                    } else if (tag == TAG_VIDEO && el.hasAttr("poster")) {
                        el.attr("poster")
                    } else {
                        ""
                    }
                }

                TAG_OBJECT -> {
                    if (el.hasAttr("data")) el.attr("data") else ""
                }

                else -> {
                    ""
                }
            }

        val fromAttribute = raw.isNotBlank()
        if (!fromAttribute) {
            valueClassText(el, resolver)?.let { rawValue -> return normalizeUrl(rawValue, resolver) }

            val attributed: String =
                when (tag) {
                    TAG_ABBR -> if (el.hasAttr("title")) el.attr("title") else ""
                    TAG_DATA, TAG_INPUT -> if (el.hasAttr("value")) el.attr("value") else ""
                    else -> ""
                }
            if (attributed.isNotBlank()) {
                return normalizeUrl(attributed, resolver)
            }

            val text = textContent(el, resolver)
            if (text.isBlank()) {
                return null
            }
            return normalizeUrl(text, resolver)
        }
        return normalizeUrl(raw, resolver)
    }

    /** Parse an `img` element for src and alt, returning a URL string or a {value, alt} object. */
    fun parseImg(
        el: Element,
        resolver: (String) -> String?,
    ): Any? {
        val src = el.attr("src")
        if (src.isBlank()) {
            return null
        }
        val resolved = normalizeUrl(src, resolver) ?: src
        return if (el.hasAttr("alt")) {
            val node = JsonNodeFactory.instance.objectNode()
            node.put("value", resolved)
            node.put("alt", el.attr("alt"))
            node
        } else {
            resolved
        }
    }

    /** Parse an `e-` property value into a {html, value} object. */
    fun parseE(
        el: Element,
        resolver: (String) -> String?,
    ): ObjectNode {
        val node = JsonNodeFactory.instance.objectNode()
        node.put("html", innerHtml(el))
        node.put("value", textContent(el, resolver))
        return node
    }

    /**
     * Parse a `dt-` property value. Produces a [DtResult] that distinguishes a
     * date-bearing value from a time-only value so the caller can apply the
     * cross-property date adoption rule.
     */
    fun parseDt(
        el: Element,
        resolver: (String) -> String?,
    ): DtResult {
        val valueElements = valueElements(el)
        val rawValues =
            if (valueElements.isNotEmpty()) {
                valueElements.map { valueText(it, resolver) }
            } else {
                emptyList()
            }

        val explicit =
            when {
                rawValues.isNotEmpty() -> {
                    rawValues
                }

                else -> {
                    val tag = el.tagName()
                    val direct: String? =
                        when (tag) {
                            TAG_TIME, TAG_INS, TAG_DEL -> if (el.hasAttr("datetime")) el.attr("datetime") else null
                            TAG_ABBR -> if (el.hasAttr("title")) el.attr("title") else null
                            TAG_DATA, TAG_INPUT -> if (el.hasAttr("value")) el.attr("value") else null
                            else -> null
                        }
                    if (direct != null) listOf(direct) else emptyList()
                }
            }

        return assembleDt(explicit, if (explicit.isEmpty()) textContent(el, resolver) else null)
    }

    private fun assembleDt(
        tokens: List<String>,
        fallbackText: String?,
    ): DtResult {
        var date: String? = null
        var time: String? = null
        var tz: String? = null

        val effectiveTokens = if (tokens.isEmpty() && fallbackText != null) listOf(fallbackText) else tokens

        for (token in effectiveTokens) {
            val t = token.trim()
            if (t.isEmpty()) continue

            val dateTime = DATE_TIME.matchEntire(t)
            if (dateTime != null) {
                if (date == null) date = dateTime.groupValues[1]
                parseTimeAndTz(dateTime.groupValues[2])?.let { (parsedTime, parsedTz) ->
                    time = parsedTime
                    tz = parsedTz
                }
                continue
            }

            when {
                DATE_ONLY.matches(t) || DATE_ORDINAL.matches(t) -> {
                    if (date == null) date = normalizeDate(t)
                }

                TIMEZONE.matches(t) -> {
                    if (tz == null) tz = normalizeTz(t)
                }

                else -> {
                    parseTimeAndTz(t)?.let { (parsedTime, parsedTz) ->
                        time = parsedTime
                        tz = parsedTz
                    }
                }
            }
        }

        if (date != null && time != null) {
            return DtResult(date = date, value = join(date, time, tz), hasTime = true)
        }
        if (date != null) {
            return DtResult(date = date, value = date, hasTime = false)
        }
        if (time != null) {
            return DtResult(date = null, value = joinTime(time, tz), hasTime = true)
        }
        val text = fallbackText ?: tokens.joinToString(" ").trim()
        return DtResult(date = null, value = text, hasTime = false)
    }

    private fun join(
        date: String,
        time: String,
        tz: String?,
    ): String = "$date ${joinTime(time, tz)}"

    private fun joinTime(
        time: String,
        tz: String?,
    ): String = if (tz != null) "$time$tz" else time

    /** Parse a time token; returns (formatted time, normalized tz) or null if not a time. */
    private fun parseTimeAndTz(token: String): Pair<String, String?>? {
        val match = TIME_WITH_TZ.matchEntire(token) ?: return null
        var hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutesRaw = match.groupValues[2]
        val secondsRaw = match.groupValues[3]
        val ampm = match.groupValues[4]
        val tzRaw = match.groupValues[5]

        if (hours > 24) return null
        val ampmLower = ampm.lowercase().replace(".", "")
        when (ampmLower) {
            "am" -> if (hours == 12) hours = 0
            "pm" -> if (hours < 12) hours += 12
        }
        if (minutesRaw.isNotEmpty() && (minutesRaw.toIntOrNull() ?: -1) > 59) return null
        if (secondsRaw.isNotEmpty() && (secondsRaw.toIntOrNull() ?: -1) > 60) return null

        val minutes = minutesRaw.ifEmpty { "00" }.padStart(2, '0')
        val hh = hours.toString().padStart(2, '0')
        val time = if (secondsRaw.isNotEmpty()) "$hh:$minutes:$secondsRaw" else "$hh:$minutes"
        val tz = tzRaw?.takeIf { it.isNotBlank() }?.let { normalizeTz(it) }
        return Pair(time, tz)
    }

    private fun normalizeDate(raw: String): String {
        if (DATE_ONLY.matches(raw) || DATE_ORDINAL.matches(raw)) return raw
        return raw
    }

    /**
     * Normalizes a timezone offset per the parsing specification: a zero
     * offset becomes "Z", any other offset becomes "±HH:MM".
     */
    private fun normalizeTz(raw: String): String {
        if (raw == "Z") return "Z"
        val sign = if (raw.startsWith("-")) "-" else "+"
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 4) return raw
        val hh = digits.take(2)
        val mm = digits.drop(2).take(2)
        return if (hh == "00" && mm == "00") "Z" else "$sign$hh:$mm"
    }

    /**
     * The first level of descendant `value`/`value-title` elements of the given
     * element, per the value-class-pattern "not deeper than one level" rule.
     */
    private fun valueElements(el: Element): List<Element> {
        val out = mutableListOf<Element>()

        fun scan(node: Node) {
            if (node !is Element) return
            if (CLASS_VALUE in node.classNames() || CLASS_VALUE_TITLE in node.classNames()) {
                out.add(node)
                return
            }
            for (child in node.children()) scan(child)
        }
        for (child in el.children()) scan(child)
        return out
    }

    private fun valueText(
        el: Element,
        resolver: (String) -> String?,
    ): String {
        if (el.classNames().contains(CLASS_VALUE_TITLE)) {
            return if (el.hasAttr("title")) el.attr("title") else textContent(el, resolver)
        }
        return when (el.tagName()) {
            TAG_IMG, TAG_AREA -> {
                el.attr("alt")
            }

            TAG_DATA -> {
                if (el.hasAttr("value")) el.attr("value") else textContent(el, resolver)
            }

            TAG_ABBR -> {
                if (el.hasAttr("title")) el.attr("title") else textContent(el, resolver)
            }

            TAG_TIME, TAG_INS, TAG_DEL -> {
                if (el.hasAttr("datetime")) el.attr("datetime") else textContent(el, resolver)
            }

            else -> {
                textContent(el, resolver)
            }
        }
    }

    /**
     * Attempts to read a value-class-pattern value from the element. Returns
     * null when the element has no descendant `value` elements. Multiple values
     * are concatenated without separators.
     */
    private fun valueClassText(
        el: Element,
        resolver: (String) -> String?,
    ): String? {
        val valueEls = valueElements(el)
        if (valueEls.isEmpty()) {
            return null
        }
        val joined = valueEls.joinToString("") { valueText(it, resolver) }
        return joined.trim()
    }

    private fun normalizeUrl(
        raw: String,
        resolver: (String) -> String?,
    ): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return resolver(trimmed)
    }
}

/**
 * The parsed value of a `dt-` property. [date] is non-null when the authored
 * value carries a specific date; [hasTime] indicates a parseable time was found
 * (with or without a date) so the caller can apply date adoption.
 */
internal data class DtResult(
    val date: String?,
    val value: String,
    val hasTime: Boolean,
)
