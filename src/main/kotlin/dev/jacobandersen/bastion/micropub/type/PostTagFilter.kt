package dev.jacobandersen.bastion.micropub.type

/**
 * Tag selection for the public post feed.
 *
 * Ordinary tags use OR semantics. [includeUntagged] is represented by the
 * reserved `none` query value and is combined with ordinary tags using the
 * same OR semantics.
 */
data class PostTagFilter(
    val tags: Set<String> = emptySet(),
    val includeUntagged: Boolean = false,
) {
    companion object {
        const val UNTAGGED = "none"

        fun parse(rawValues: Collection<String>?): PostTagFilter? {
            if (rawValues == null) return null

            var includeUntagged = false
            val tags = linkedSetOf<String>()
            rawValues
                .asSequence()
                .flatMap { it.splitToSequence(',') }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .forEach { value ->
                    if (value == UNTAGGED) {
                        includeUntagged = true
                    } else {
                        tags += value
                    }
                }

            return if (tags.isEmpty() && !includeUntagged) null else PostTagFilter(tags, includeUntagged)
        }
    }
}
