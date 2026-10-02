package dev.jacobandersen.bastion.micropub.syndication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GraphemesTest {
    @Test
    fun countsAsciiCharacters() {
        assertEquals(5, Graphemes.count("hello"))
        assertEquals(0, Graphemes.count(""))
    }

    @Test
    fun countsEmojiSequencesAsSingleGraphemes() {
        assertEquals(1, Graphemes.count("👨‍👩‍👧‍👦"))
        assertEquals(1, Graphemes.count("🇺🇸"))
        assertEquals(2, Graphemes.count("a🇺🇸"))
    }

    @Test
    fun takeKeepsEmojiIntact() {
        val text = "ab🇺🇸cd"
        val taken = Graphemes.take(text, 3)
        assertEquals("ab🇺🇸", taken)
        assertEquals(3, Graphemes.count(taken))
    }

    @Test
    fun takeReturnsEmptyForNonPositiveMax() {
        assertEquals("", Graphemes.take("hello", 0))
        assertEquals("", Graphemes.take("hello", -1))
    }

    @Test
    fun takeReturnsFullTextWhenShort() {
        assertEquals("hi", Graphemes.take("hi", 300))
    }

    @Test
    fun takenPrefixNeverExceedsMax() {
        val text = "word ".repeat(100)
        val taken = Graphemes.take(text, 60)
        assertTrue(Graphemes.count(taken) <= 60)
    }
}
