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
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.salmention.config.SalmentionConfig
import dev.jacobandersen.bastion.webmention.salmention.data.domain.SalmentionResponse
import dev.jacobandersen.bastion.webmention.salmention.data.service.SalmentionResponseService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import java.time.Instant
import java.util.UUID

class SalmentionReceiverTest {
    private val salmentionResponseService = mock(SalmentionResponseService::class.java)
    private val salmentionSender = mock(SalmentionSender::class.java)
    private val postService = mock(PostService::class.java)
    private val urlService = mock(UrlService::class.java)

    private val sourceUrl = "https://source.example/reply"
    private val carolUrl = "https://carol.example/reply"
    private val daveUrl = "https://dave.example/reply"
    private val postId = UUID.randomUUID()
    private val receivedWebmentionId = UUID.randomUUID()
    private val postUrl = "https://bastion.test/2026/09/07/post"

    private fun receiver(enabled: Boolean = true) =
        SalmentionReceiver(
            salmentionResponseService,
            salmentionSender,
            postService,
            urlService,
            SalmentionConfig(
                enabled = enabled,
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

    private fun stored(
        responseUrl: String,
        contentText: String? = null,
    ): SalmentionResponse {
        val now = Instant.now()
        return SalmentionResponse(
            id = UUID.randomUUID(),
            receivedWebmentionId = receivedWebmentionId,
            sourceUrl = sourceUrl,
            responseUrl = responseUrl,
            interaction = WebmentionInteraction.MENTION,
            authorName = null,
            authorUrl = null,
            authorPhoto = null,
            contentText = contentText,
            contentHtml = null,
            rawMf2 = null,
            firstSeenAt = now,
            updatedAtUtc = now,
        )
    }

    @Test
    fun `first acceptance triggers the resend without ingesting`() {
        stubPost()

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(listOf(carolUrl)),
            isReReceipt = false,
            receivedResponseUpdated = false,
        )

        verify(salmentionSender).resendToActiveTargets(postUrl)
        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
    }

    @Test
    fun `re-receipt ingests new nested responses and resends`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId)).thenReturn(emptyList())

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(listOf(carolUrl)),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        verify(
            salmentionResponseService,
            times(1),
        ).ingest(eq(sourceUrl), eq(receivedWebmentionId), eq(carolUrl), any())
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `a re-receipt right after another still ingests newly appeared nested responses`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId)).thenReturn(emptyList())

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(emptyList()),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )
        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(listOf(carolUrl)),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        verify(salmentionResponseService, times(1)).ingest(eq(sourceUrl), eq(receivedWebmentionId), eq(carolUrl), any())
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt with unchanged responses and no content update is a no-op`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId))
            .thenReturn(listOf(stored(carolUrl)))

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(listOf(carolUrl)),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
        verify(salmentionResponseService, never()).refresh(any(), any(), any())
        verify(salmentionResponseService, never()).retireRemoved(any(), any())
        verify(salmentionSender, never()).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt that only updates the received response resends`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId))
            .thenReturn(listOf(stored(carolUrl)))

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(listOf(carolUrl)),
            isReReceipt = true,
            receivedResponseUpdated = true,
        )

        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt refreshes a stored response whose content changed on the source`() {
        stubPost()
        val html =
            """
            <article class="h-entry">
              <p class="e-content">Bob's reply</p>
              <div class="h-entry">
                <a class="u-url" href="$carolUrl">Carol's reply</a>
                <div class="e-content">edited text</div>
              </div>
            </article>
            """.trimIndent()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId))
            .thenReturn(listOf(stored(carolUrl, contentText = "original text")))

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = Mf2ParserImpl().parse(html, sourceUrl),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        verify(salmentionResponseService).refresh(eq(receivedWebmentionId), eq(carolUrl), any())
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt retires a stored response that is no longer on the source`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId))
            .thenReturn(listOf(stored(carolUrl), stored(daveUrl)))

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(listOf(carolUrl)),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        verify(salmentionResponseService).retireRemoved(eq(receivedWebmentionId), eq(setOf(carolUrl)))
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt ingests every new nested response without a count limit`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId)).thenReturn(emptyList())
        val urls = (1..25).map { "https://responder$it.example/reply" }

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = parse(urls),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        urls.forEach { url ->
            verify(salmentionResponseService).ingest(eq(sourceUrl), eq(receivedWebmentionId), eq(url), any())
        }
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt ignores an in-reply-to h-cite relation target`() {
        stubPost()
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId)).thenReturn(emptyList())
        val html =
            """
            <article class="h-entry">
              <p class="e-content">Bob's reply</p>
              <a class="u-in-reply-to h-cite" href="$postUrl">the post</a>
            </article>
            """.trimIndent()

        receiver().handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = Mf2ParserImpl().parse(html, sourceUrl),
            isReReceipt = true,
            receivedResponseUpdated = false,
        )

        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
        verify(salmentionSender, never()).resendToActiveTargets(postUrl)
    }

    @Test
    fun `gone retires the nested responses and resends when rows were retired`() {
        stubPost()
        `when`(salmentionResponseService.retireByReceivedWebmention(receivedWebmentionId)).thenReturn(2)

        receiver().handleGone(sourceUrl, receivedWebmentionId, postId)

        verify(salmentionResponseService).retireByReceivedWebmention(receivedWebmentionId)
        verify(salmentionSender).resendToActiveTargets(postUrl)
    }

    @Test
    fun `gone does not resend when nothing was stored`() {
        stubPost()
        `when`(salmentionResponseService.retireByReceivedWebmention(receivedWebmentionId)).thenReturn(0)

        receiver().handleGone(sourceUrl, receivedWebmentionId, postId)

        verify(salmentionResponseService).retireByReceivedWebmention(receivedWebmentionId)
        verify(salmentionSender, never()).resendToActiveTargets(any())
    }

    @Test
    fun `disabled receiver does nothing`() {
        receiver(enabled = false).handleVerified(
            sourceUrl = sourceUrl,
            receivedWebmentionId = receivedWebmentionId,
            postId = postId,
            parseResult = null,
            isReReceipt = false,
            receivedResponseUpdated = false,
        )
        receiver(enabled = false).handleGone(sourceUrl, receivedWebmentionId, postId)

        verify(salmentionSender, never()).resendToActiveTargets(any())
        verify(salmentionResponseService, never()).retireByReceivedWebmention(any())
        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
    }

    @Test
    fun `diffAndIngest analyzes and ingests each new response`() {
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId)).thenReturn(emptyList())

        val ingested =
            receiver().diffAndIngest(
                sourceUrl = sourceUrl,
                receivedWebmentionId = receivedWebmentionId,
                parseResult = parse(listOf(carolUrl)),
            )

        assertTrue(ingested)
        verify(salmentionResponseService, times(1))
            .ingest(
                eq(sourceUrl),
                eq(receivedWebmentionId),
                eq(carolUrl),
                check<ReceivedWebmentionAnalysis> {
                    it.interaction == WebmentionInteraction.MENTION
                },
            )
    }

    @Test
    fun `diffAndIngest is a no-op when nothing changed`() {
        `when`(salmentionResponseService.byReceivedWebmention(receivedWebmentionId))
            .thenReturn(listOf(stored(carolUrl)))

        val changed =
            receiver().diffAndIngest(
                sourceUrl = sourceUrl,
                receivedWebmentionId = receivedWebmentionId,
                parseResult = parse(listOf(carolUrl)),
            )

        assertFalse(changed)
        verify(salmentionResponseService, never()).ingest(any(), any(), any(), any())
        verify(salmentionResponseService, never()).refresh(any(), any(), any())
        verify(salmentionResponseService, never()).retireRemoved(any(), any())
    }
}
