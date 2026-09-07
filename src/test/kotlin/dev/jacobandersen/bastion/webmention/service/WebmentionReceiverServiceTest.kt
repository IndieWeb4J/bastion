package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2Parser
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmention
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.http.SourceFetch
import dev.jacobandersen.bastion.webmention.http.WebmentionSourceFetcher
import dev.jacobandersen.bastion.webmention.salmention.service.SalmentionReceiver
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.time.Instant
import java.util.UUID

class WebmentionReceiverServiceTest {
    private val notificationService = mock(ReceivedWebmentionService::class.java)
    private val sourceFetcher = mock(WebmentionSourceFetcher::class.java)
    private val parser = mock(Mf2Parser::class.java)
    private val salmentionReceiver = mock(SalmentionReceiver::class.java)
    private val receiver = WebmentionReceiverService(notificationService, sourceFetcher, parser, salmentionReceiver)

    private val sourceUrl = "https://source.example/reply"
    private val targetUrl = "https://bastion.test/2026/01/01/post"
    private val postId = UUID.randomUUID()

    private fun fetch(
        status: Int = 200,
        body: String = "<a href=\"$targetUrl\">link</a>",
        contentType: String = "text/html",
    ) = SourceFetch(status, sourceUrl, contentType, body)

    private fun givenPending(
        interaction: WebmentionInteraction? = null,
        contentText: String? = null,
    ) {
        val now = Instant.now()
        `when`(notificationService.ensurePending(anyString(), anyString(), any()))
            .thenReturn(
                ReceivedWebmention(
                    id = UUID.randomUUID(),
                    postId = postId,
                    sourceUrl = sourceUrl,
                    targetUrl = targetUrl,
                    state = ReceivedWebmentionState.PENDING,
                    interaction = interaction,
                    authorName = null,
                    authorUrl = null,
                    authorPhoto = null,
                    contentText = contentText,
                    contentHtml = null,
                    rawMf2 = null,
                    lastError = null,
                    firstSeenAt = now,
                    verifiedAt = null,
                    updatedAtUtc = now,
                ),
            )
    }

    private fun replyParse(content: String): Mf2ParseResult =
        Mf2ParseResult(
            items =
                listOf(
                    Mf2Object(
                        type = listOf("h-entry"),
                        properties = mapOf("content" to listOf(Mf2Value.String(content))),
                    ),
                ),
            rels = mapOf("in-reply-to" to listOf(targetUrl)),
            relUrls = emptyMap(),
        )

    @Test
    fun `verifies a linking html source and stores the analysis`() {
        givenPending()
        val parseResult =
            Mf2ParseResult(
                items =
                    listOf(
                        Mf2Object(
                            type = listOf("h-entry"),
                            properties = mapOf("content" to listOf(Mf2Value.String("hi"))),
                        ),
                    ),
                rels = emptyMap(),
                relUrls = emptyMap(),
            )
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch())
        `when`(parser.parse(anyString(), anyString())).thenReturn(parseResult)

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markVerified(
            org.mockito.kotlin.eq(sourceUrl),
            org.mockito.kotlin.eq(postId),
            org.mockito.kotlin.check { analysis ->
                org.junit.jupiter.api.Assertions
                    .assertEquals(WebmentionInteraction.MENTION, analysis.interaction)
            },
        )
    }

    @Test
    fun `marks the webmention deleted when the source is gone`() {
        givenPending()
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch(status = 410, body = ""))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markDeleted(sourceUrl, postId)
    }

    @Test
    fun `rejects the webmention when the source does not link`() {
        givenPending()
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch(body = "<p>no link</p>"))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markRejected(sourceUrl, postId, "source does not link to the target")
    }

    @Test
    fun `flags the webmention when the source is unreachable`() {
        givenPending()
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(
            SourceFetch(0, sourceUrl, null, "", error = "connection refused"),
        )

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markError(sourceUrl, postId, "connection refused")
    }

    @Test
    fun `verifies a source whose document has no microformats as a plain mention`() {
        givenPending()
        val emptyParse = Mf2ParseResult(items = emptyList(), rels = emptyMap(), relUrls = emptyMap())
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch())
        `when`(parser.parse(anyString(), anyString())).thenReturn(emptyParse)

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markVerified(
            org.mockito.kotlin.eq(sourceUrl),
            org.mockito.kotlin.eq(postId),
            org.mockito.kotlin.check { analysis ->
                org.junit.jupiter.api.Assertions
                    .assertEquals(WebmentionInteraction.MENTION, analysis.interaction)
                org.junit.jupiter.api.Assertions
                    .assertNull(analysis.primary)
            },
        )
    }

    @Test
    fun `first acceptance reports no received-response update to the salmention receiver`() {
        givenPending()
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch())
        `when`(parser.parse(anyString(), anyString())).thenReturn(replyParse("new"))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(salmentionReceiver).handleVerified(
            org.mockito.kotlin.eq(sourceUrl),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(postId),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(false),
            org.mockito.kotlin.eq(false),
        )
    }

    @Test
    fun `re-verification with changed content reports a received-response update`() {
        givenPending(interaction = WebmentionInteraction.REPLY, contentText = "old")
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch())
        `when`(parser.parse(anyString(), anyString())).thenReturn(replyParse("new"))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(salmentionReceiver).handleVerified(
            org.mockito.kotlin.eq(sourceUrl),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(postId),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(true),
            org.mockito.kotlin.eq(true),
        )
    }

    @Test
    fun `re-verification with identical content reports no received-response update`() {
        givenPending(interaction = WebmentionInteraction.REPLY, contentText = "same")
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch())
        `when`(parser.parse(anyString(), anyString())).thenReturn(replyParse("same"))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(salmentionReceiver).handleVerified(
            org.mockito.kotlin.eq(sourceUrl),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(postId),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.eq(true),
            org.mockito.kotlin.eq(false),
        )
    }
}
