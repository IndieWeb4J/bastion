package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.microformats2.Mf2ParserImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NestedResponseExtractorTest {
    private val sourceUrl = "https://source.example/reply"

    private fun extract(
        html: String,
        baseUrl: String = sourceUrl,
        source: String? = null,
    ): List<NestedResponse> =
        NestedResponseExtractor.extract(
            Mf2ParserImpl().parse(html, baseUrl),
            sourceUrl = source,
        )

    @Test
    fun `extracts a nested h-entry child carrying a u-url`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <a class="u-in-reply-to" href="https://alice.example/post">the post</a>
                  <div class="h-entry">
                    <a class="u-url" href="https://carol.example/reply">Carol's reply</a>
                    <div class="e-content">well said</div>
                  </div>
                </article>
                """.trimIndent(),
            )

        assertEquals(1, responses.size)
        assertEquals("https://carol.example/reply", responses[0].responseUrl)
        assertTrue(responses[0].entry.type.contains("h-entry"))
    }

    @Test
    fun `extracts a nested h-cite stored as a property value`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <div class="p-comment h-cite">
                    <a class="u-url" href="https://carol.example/reply">Carol</a>
                    <div class="e-content">well said</div>
                  </div>
                </article>
                """.trimIndent(),
            )

        assertEquals(1, responses.size)
        assertEquals("https://carol.example/reply", responses[0].responseUrl)
        assertTrue(responses[0].entry.type.contains("h-cite"))
    }

    @Test
    fun `ignores a source entry's in-reply-to relation h-cite`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <a class="u-in-reply-to h-cite" href="https://alice.example/post">the post</a>
                </article>
                """.trimIndent(),
            )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun `ignores like repost and syndication relation h-cites`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <a class="u-like-of h-cite" href="https://alice.example/post">a like</a>
                  <a class="u-repost-of h-cite" href="https://carol.example/repost">a repost</a>
                  <a class="u-syndication h-cite" href="https://twitter.example/status/1">a copy</a>
                </article>
                """.trimIndent(),
            )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun `extracts nested entries while ignoring their in-reply-to relation targets`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <div class="h-entry">
                    <a class="u-url" href="https://carol.example/reply">Carol's reply</a>
                    <a class="u-in-reply-to h-cite" href="https://alice.example/post">the post</a>
                  </div>
                </article>
                """.trimIndent(),
            )

        assertEquals(1, responses.size)
        assertEquals("https://carol.example/reply", responses[0].responseUrl)
    }

    @Test
    fun `ignores nested objects without a u-url`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <div class="h-entry">
                    <div class="e-content">no permalink here</div>
                  </div>
                </article>
                """.trimIndent(),
            )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun `ignores nested non-response microformats like h-card`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <span class="p-author h-card">
                    <a class="u-url" href="https://bob.example/">Bob</a>
                  </span>
                </article>
                """.trimIndent(),
            )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun `extracts deeply nested responses`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                  <div class="h-entry">
                    <a class="u-url" href="https://carol.example/reply">Carol</a>
                    <div class="h-entry">
                      <a class="u-url" href="https://dave.example/reply">Dave</a>
                    </div>
                  </div>
                </article>
                """.trimIndent(),
            )

        assertEquals(
            setOf("https://carol.example/reply", "https://dave.example/reply"),
            responses.map { it.responseUrl }.toSet(),
        )
    }

    @Test
    fun `does not treat the top-level source object as a nested response`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <a class="u-url" href="https://source.example/reply">the source itself</a>
                  <p class="e-content">Bob's reply</p>
                </article>
                """.trimIndent(),
            )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun `collects a top-level sibling h-cite that replies to the received source`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <a class="u-url" href="$sourceUrl">Bob's reply</a>
                  <p class="e-content">Bob's reply</p>
                </article>
                <div class="h-cite">
                  <a class="u-url" href="https://carol.example/reply">Carol's reply</a>
                  <a class="u-in-reply-to" href="$sourceUrl">in reply to Bob</a>
                </div>
                """.trimIndent(),
                source = sourceUrl,
            )

        assertEquals(listOf("https://carol.example/reply"), responses.map { it.responseUrl })
    }

    @Test
    fun `collects a top-level sibling h-entry that comments on the received source`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <a class="u-url" href="$sourceUrl">Bob's reply</a>
                  <p class="e-content">Bob's reply</p>
                </article>
                <div class="h-entry">
                  <a class="u-url" href="https://carol.example/reply">Carol's reply</a>
                  <a class="u-comment-of" href="$sourceUrl">a comment on Bob</a>
                </div>
                """.trimIndent(),
                source = sourceUrl,
            )

        assertEquals(listOf("https://carol.example/reply"), responses.map { it.responseUrl })
    }

    @Test
    fun `ignores top-level siblings that reply to something other than the received source`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <a class="u-url" href="$sourceUrl">Bob's reply</a>
                  <p class="e-content">Bob's reply</p>
                </article>
                <div class="h-cite">
                  <a class="u-url" href="https://carol.example/reply">Carol's reply</a>
                  <a class="u-in-reply-to" href="https://other.example/post">in reply to someone else</a>
                </div>
                """.trimIndent(),
                source = sourceUrl,
            )

        assertTrue(responses.isEmpty())
    }

    @Test
    fun `collects top-level siblings replying to the received source without a DOM-nested source`() {
        val responses =
            extract(
                """
                <article class="h-entry">
                  <p class="e-content">Bob's reply</p>
                </article>
                <div class="h-cite">
                  <a class="u-url" href="https://carol.example/reply">Carol's reply</a>
                  <a class="u-in-reply-to" href="$sourceUrl">in reply to Bob</a>
                </div>
                """.trimIndent(),
                source = sourceUrl,
            )

        assertEquals(listOf("https://carol.example/reply"), responses.map { it.responseUrl })
    }
}
