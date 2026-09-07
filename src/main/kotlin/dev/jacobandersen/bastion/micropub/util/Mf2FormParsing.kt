package dev.jacobandersen.bastion.micropub.util

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value

/**
 * Converts a Micropub form-encoded parameter map into an [Mf2Object] with a
 * single `h-*` type taken from the `h` parameter (defaulting to `h-entry`).
 * Form values are strings by definition of the encoding.
 */
internal fun Map<String, Array<String>>.toMf2Object(): Mf2Object {
    val h = this["h"]?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "entry"
    val properties = this
        .filterKeys { it != "h" }
        .mapValues { (_, values) -> values.map(Mf2Value::String) }
    return Mf2Object(type = listOf("h-$h"), properties = properties, children = null)
}
