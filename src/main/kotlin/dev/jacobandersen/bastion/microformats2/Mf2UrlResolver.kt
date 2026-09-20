package dev.jacobandersen.bastion.microformats2

import java.net.URI

/**
 * URL normalization for microformats parsing. Resolves a raw value (an
 * absolute URL, a scheme-relative `//host` reference, or a relative reference)
 * against a document base URL, per the microformats2 parsing specification.
 */
internal object Mf2UrlResolver {
    fun resolve(
        base: String,
        raw: String,
    ): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (base.isEmpty()) {
            return if (isAbsolute(trimmed)) trimmed else null
        }
        val parsed = runCatching { URI(base) }.getOrNull() ?: return null
        return runCatching { parsed.resolve(trimmed).toString() }.getOrNull()
    }

    private fun isAbsolute(raw: String): Boolean = raw.contains(":") && runCatching { URI(raw).isAbsolute }.getOrDefault(false)
}
