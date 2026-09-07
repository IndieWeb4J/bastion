package dev.jacobandersen.bastion.webmention.salmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2ParserImpl
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.salmention.config.SalmentionConfig
import dev.jacobandersen.bastion.webmention.salmention.data.service.SalmentionResponseService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import java.time.Instant
import java.util.UUID

class SalmentionReceiverTest {
    private val salmentionResponseService = mock(SalmentionResponseService::class.java)
    private val salmentionSender = mock(SalmentionSender::class.java)
    private val receivedWebmentionService = mock(ReceivedWebmentionService::class.java)
    private val postService = mock(PostService::class.java)
    private val urlService = mock(UrlService::class.java)

    private val sourceUrl = "https://source.example/reply"
    private val postId = UUID.randomUUID()
    private val receivedWebmentionId = UUID.randomUUID()
    private val postUrl = "https://bastion.test/2026/09/07/post"

    private fun receiver(
        enabled: Boolean = true,
        maxNestedResponsesPerSource: Int = 20,
        recheckCooldownMinutes: Long = 15,
    ) = SalmentionReceiver(
        salmentionResponseService,
        salmentionSender,
        receivedWebmentionService,
        postService,
        urlService,
        SalmentionConfig(
            enabled = enabled,
            maxNestedResponsesPerSource = maxNestedResponsesPerSource,
            recheckCooldownMinutes = recheckCooldownMinutes,
        ),
    )

    private fun stubPost() {
        val post =
            Post(
                id = postId,
                slug = "post",
                status = PostStatus.PUBLISHED,
                visibility = PostVisibility.PUBLIC,
                deleted = false,
                type = "h-entry",
                subtype = null,
                post =
                    Mf2Object(
                        type = listOf("h-entry"),
                        properties = mapOf("published" to listOf(Mf2Value.String("2026-09-07T00:00:00Z"))),
                    ),
            )
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(urlService.generatePostUrl(post)).thenReturn(postUrl)
    }

    private fun parse(nestedUrls: List<String>): Mf2ParseResult {
        val children =
            nestedUrls.joinToString("\n") { url ->
                """<div class="h-entry"><a class="u-url" href="$url">r</a></div>"""
            }
        val html =
            """
            <article class="h-entry">
              <p class="e-content">Bob's reply</p>
              $children
            </article>
            """.trimIndent()
        return Mf2ParserImpl().parse(html, sourceUrl)
    }

    @Test
    fun `first acceptance triggers the resend without ingesting`() {
        stubPost()

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            lastRecheckedAt = null,
            parseResult = parse(listOf("https://carol.example/reply")),
            isReReceipt = false,
        )

        verify(salmentionSender).resendToActiveTargets(postUrl)
        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
    }

    @Test
    fun `re-receipt ingests new nested responses and resends`() {
        stubPost()
        `when`(salmentionResponseService.responseUrlsBySourceUrl(sourceUrl)).thenReturn(emptySet())

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            lastRecheckedAt = null,
            parseResult = parse(listOf("https://carol.example/reply")),
            isReReceipt = true,
        )

        verify(
            salmentionResponseService,
            times(1),
        ).ingest(eq(sourceUrl), eq(receivedWebmentionId), eq("https://carol.example/reply"), any())
        verify(receivedWebmentionService).markRechecked(eq(sourceUrl), eq(postId), any())
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt with no new responses is a no-op`() {
        stubPost()
        `when`(salmentionResponseService.responseUrlsBySourceUrl(sourceUrl))
            .thenReturn(setOf("https://carol.example/reply"))

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            lastRecheckedAt = null,
            parseResult = parse(listOf("https://carol.example/reply")),
            isReReceipt = true,
        )

        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
        verify(salmentionSender, never()).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt within the cooldown window is skipped`() {
        stubPost()

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            lastRecheckedAt = Instant.now(),
            parseResult = parse(listOf("https://carol.example/reply")),
            isReReceipt = true,
        )

        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
        verify(receivedWebmentionService, never()).markRechecked(any(), any(), any())
        verify(salmentionSender, never()).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt respects the per-source nested response limit`() {
        stubPost()
        `when`(salmentionResponseService.responseUrlsBySourceUrl(sourceUrl)).thenReturn(emptySet())

        receiver(maxNestedResponsesPerSource = 1).handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            lastRecheckedAt = null,
            parseResult = parse(listOf("https://carol.example/reply", "https://dave.example/reply")),
            isReReceipt = true,
        )

        verify(salmentionResponseService, times(1)).ingest(any(), any(), any(), any())
    }

    @Test
    fun `gone retires the nested responses`() {
        receiver().handleGone(sourceUrl, receivedWebmentionId)

        verify(salmentionResponseService).retireByReceivedWebmention(receivedWebmentionId)
    }

    @Test
    fun `disabled receiver does nothing`() {
        receiver(enabled = false).handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            lastRecheckedAt = null,
            parseResult = null,
            isReReceipt = false,
        )
        receiver(enabled = false).handleGone(sourceUrl, receivedWebmentionId)

        verify(salmentionSender, never()).resendToActiveTargets(any())
        verify(salmentionResponseService, never()).retireByReceivedWebmention(any())
        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
    }

    @Test
    fun `diffAndIngest analyzes and ingests each new response`() {
        `when`(salmentionResponseService.responseUrlsBySourceUrl(sourceUrl)).thenReturn(emptySet())

        val ingested =
            receiver().diffAndIngest(
                sourceUrl = sourceUrl,
                receivedWebmentionId = receivedWebmentionId,
                parseResult = parse(listOf("https://carol.example/reply")),
            )

        org.junit.jupiter.api.Assertions
            .assertTrue(ingested)
        verify(salmentionResponseService, times(1))
            .ingest(
                eq(sourceUrl),
                eq(receivedWebmentionId),
                eq("https://carol.example/reply"),
                org.mockito.kotlin.check<ReceivedWebmentionAnalysis> {
                    it.interaction ==
                        dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction.MENTION
                },
            )
    }
}
