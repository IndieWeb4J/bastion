package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object

/**
 * A nested h-entry/h-cite discovered within a source document: its resolved
 * `u-url` (the response's own permalink, used as the dedupe key) and the parsed
 * microformat object it came from.
 */
data class NestedResponse(
    val responseUrl: String,
    val entry: Mf2Object,
)
