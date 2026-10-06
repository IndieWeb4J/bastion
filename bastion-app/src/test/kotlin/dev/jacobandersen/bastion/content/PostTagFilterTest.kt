package dev.jacobandersen.bastion.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PostTagFilterTest {
    @Test
    fun `parses untagged sentinel case insensitively and combines ordinary tags`() {
        assertEquals(
            PostTagFilter(tags = setOf("kotlin", "java"), includeUntagged = true),
            PostTagFilter.parse(listOf(" NONE, Kotlin ", "JAVA", "kotlin")),
        )
    }

    @Test
    fun `blank and missing values do not create a filter`() {
        assertNull(PostTagFilter.parse(null))
        assertNull(PostTagFilter.parse(emptyList()))
        assertNull(PostTagFilter.parse(listOf("", "  ", ", ")))
    }
}
