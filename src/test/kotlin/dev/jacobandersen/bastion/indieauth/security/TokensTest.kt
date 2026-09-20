package dev.jacobandersen.bastion.indieauth.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TokensTest {
    @Test
    fun `random tokens are unique and have full entropy length`() {
        val a = Tokens.random()
        val b = Tokens.random()

        assertNotEquals(a, b)
        assertEquals(43, a.length)
    }

    @Test
    fun `sha256 is deterministic and hex-encoded`() {
        assertEquals(Tokens.sha256("x"), Tokens.sha256("x"))
        assertNotEquals(Tokens.sha256("x"), Tokens.sha256("y"))
        assertTrue(Tokens.sha256("x").matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `constantTimeEquals compares correctly`() {
        assertTrue(Tokens.constantTimeEquals("abc", "abc"))
        assertFalse(Tokens.constantTimeEquals("abc", "abd"))
    }
}
