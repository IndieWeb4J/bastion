package dev.jacobandersen.bastion.indieauth.type

/**
 * Space-delimited scope string handling, matching IndieAuth/OAuth's `scope`
 * wire format. Scopes are opaque strings to the IndieAuth provider; Micropub
 * interprets the ones it understands.
 */
object Scopes {
    /** Splits a space-delimited scope string into its distinct, non-blank tokens. */
    fun parse(scope: String?): List<String> =
        scope
            .orEmpty()
            .split(' ')
            .filter { it.isNotBlank() }

    /** Joins distinct, non-blank scopes back into a single space-delimited string. */
    fun join(scopes: Collection<String>): String =
        scopes
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
}
