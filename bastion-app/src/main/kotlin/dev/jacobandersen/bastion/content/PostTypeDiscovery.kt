package dev.jacobandersen.bastion.content

import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.microformats2.plainTextOrNull

/**
 * Post-type discovery (PTD) in Kotlin, replacing the former PL/pgSQL
 * `post_type_discovery` (and its `jsonb_*` helpers) so the rules are testable
 * and evolve without a database migration.
 *
 * Operates on the canonical mf2 object. Returns the derived post type
 * (`note`/`article`/`reply`/`repost`/`like`/`rsvp`/`photo`/`video`/`bookmark`/
 * `checkin`/`mood`), or null when the object is not an `h-entry` (non-entry
 * types have no post type). The current rule set mirrors the historical
 * SQL exactly; golden tests pin it.
 */
object PostTypeDiscovery {
    private val WS = Regex("\\s+")

    fun discover(post: Mf2Object): String? {
        if (post.primaryType() != "h-entry") return null

        if (hasText(post, "rsvp")) return "rsvp"
        if (anyUrl(post, "in-reply-to")) return "reply"
        if (anyUrl(post, "repost-of")) return "repost"
        if (anyUrl(post, "like-of")) return "like"
        if (anyUrl(post, "video")) return "video"
        if (anyUrl(post, "photo")) return "photo"
        if (anyUrl(post, "bookmark-of")) return "bookmark"
        if (anyCheckin(post)) return "checkin"
        if (hasText(post, "mood")) return "mood"

        val content = (firstText(post, "content") ?: firstText(post, "summary"))
        if (content == null) return "note"
        val normalizedContent = content.replace(WS, " ").trim()

        val name = firstText(post, "name")
        if (name == null) return "note"
        val normalizedName = name.replace(WS, " ").trim()

        return if (!normalizedContent.startsWith(normalizedName)) "article" else "note"
    }

    /** Derive the mf2 `h-*` type stored in `posts.h` (primary type, or null). */
    fun primaryType(post: Mf2Object): String? = post.primaryType()

    /** Derive the normalized, distinct, lowercased category list stored in `posts.categories`. */
    fun categories(post: Mf2Object): List<String> =
        post
            .getProperty("category")
            .mapNotNull { it.plainTextOrNull }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .distinct()
            .sorted()

    // ---------------------------------------------------------------- helpers

    /** Any value of the property yields non-blank text (mf2 string, object value, or html). */
    private fun hasText(
        post: Mf2Object,
        key: String,
    ): Boolean = post.getProperty(key).any { !it.plainTextOrNull.isNullOrBlank() }

    private fun firstText(
        post: Mf2Object,
        key: String,
    ): String? = post.getProperty(key).firstNotNullOfOrNull { it.plainTextOrNull?.takeIf(String::isNotBlank) }

    /** The first value is a text-bearing property with `name` or a plain value. */
    private fun anyCheckin(post: Mf2Object): Boolean =
        post.getProperty("checkin").any { value ->
            when (value) {
                is Mf2Value.String -> {
                    value.value.isNotBlank()
                }

                is Mf2Value.Object -> {
                    value.value.getProperty("name").any { !it.plainTextOrNull.isNullOrBlank() }
                }

                is Mf2Value.Json -> {
                    val node = value.value
                    val nameNode = node.get("properties")?.get("name")
                    val hasName = nameNode != null && nameNode.isArray && nameNode.any { it.isValueNode && it.asString().isNotBlank() }
                    val valueText =
                        node
                            .get("value")
                            ?.takeIf { it.isValueNode }
                            ?.asString()
                            ?.trim()
                            .orEmpty()
                    hasName || valueText.isNotEmpty()
                }

                else -> {
                    false
                }
            }
        }

    /** Any value of the property resolves to a valid absolute http(s) URL. */
    private fun anyUrl(
        post: Mf2Object,
        key: String,
    ): Boolean = post.getProperty(key).any { candidateUrl(it)?.let(::isValidUrl) == true }

    private fun candidateUrl(value: Mf2Value): String? =
        when (value) {
            is Mf2Value.String -> {
                value.value.trim().takeIf { it.isNotEmpty() }
            }

            is Mf2Value.Object -> {
                value.value.getProperty("url").firstNotNullOfOrNull { it.plainTextOrNull?.trim()?.takeIf(String::isNotEmpty) }
                    ?: value.value.getProperty("name").firstNotNullOfOrNull { it.plainTextOrNull?.trim()?.takeIf(String::isNotEmpty) }
            }

            is Mf2Value.Json -> {
                val node = value.value
                node
                    .get("value")
                    ?.takeIf { it.isValueNode }
                    ?.asString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: node
                        .get("url")
                        ?.takeIf { it.isValueNode }
                        ?.asString()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
            }

            else -> {
                null
            }
        }

    private val VALID_URL = Regex("^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$", RegexOption.IGNORE_CASE)

    private fun isValidUrl(url: String): Boolean = VALID_URL.matches(url)
}
