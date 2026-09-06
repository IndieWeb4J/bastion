package dev.jacobandersen.bastion.microformats2

/**
 * Parses an HTML document into the canonical `{items, rels, rel-urls}`
 * structure defined by the microformats2 parsing specification (including the
 * value-class-pattern and backward-compatibility class mappings).
 *
 * Implementations must be safe for concurrent reuse: a parse call must not
 * retain state on the instance.
 */
fun interface Mf2Parser {
    fun parse(html: String, baseUrl: String): Mf2ParseResult
}
