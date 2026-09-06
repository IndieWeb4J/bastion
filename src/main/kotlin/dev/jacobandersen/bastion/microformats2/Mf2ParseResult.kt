package dev.jacobandersen.bastion.microformats2

/**
 * The result of parsing a document for microformats, mirroring the canonical
 * JSON shape defined by the microformats2 parsing specification:
 * `{ "items": [...], "rels": {...}, "rel-urls": {...} }`.
 */
data class Mf2ParseResult(
    val items: List<Mf2Object>,
    val rels: Map<String, List<String>>,
    val relUrls: Map<String, Mf2RelUrl>,
)

/**
 * The parsed details of a single URL found via the `rel` attribute of a
 * hyperlink element, mirroring the canonical "rel-urls" hash.
 */
data class Mf2RelUrl(
    val rels: List<String>,
    val hreflang: String? = null,
    val media: String? = null,
    val title: String? = null,
    val type: String? = null,
    val text: String? = null,
)
