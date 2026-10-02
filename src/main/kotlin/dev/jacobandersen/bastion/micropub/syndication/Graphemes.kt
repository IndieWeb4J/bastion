package dev.jacobandersen.bastion.micropub.syndication

import java.text.BreakIterator

/**
 * Grapheme-cluster-aware string measurement, so syndication budgets match how
 * downstream services (e.g. Bluesky's 300-grapheme limit) count text. Backed
 * by [BreakIterator.getCharacterInstance], which keeps emoji and ZWJ sequences
 * intact instead of splitting UTF-16 code units.
 */
internal object Graphemes {
    fun count(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    fun take(
        text: String,
        max: Int,
    ): String {
        if (max <= 0) return ""
        if (text.isEmpty()) return ""
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var count = 0
        var end = 0
        while (true) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) break
            count++
            if (count > max) break
            end = next
        }
        return text.substring(0, end)
    }
}
