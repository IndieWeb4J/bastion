package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2ParseResult
import dev.jacobandersen.bastion.microformats2.Mf2Parser
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.http.SourceFetch
import dev.jacobandersen.bastion.webmention.http.WebmentionSourceFetcher
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class WebmentionReceiverServiceTest {
    private val notificationService = mock(ReceivedWebmentionService::class.java)
    private val sourceFetcher = mock(WebmentionSourceFetcher::class.java)
    private val parser = mock(Mf2Parser::class.java)
    private val receiver = WebmentionReceiverService(notificationService, sourceFetcher, parser)

    private val sourceUrl = "https://source.example/reply"
    private val targetUrl = "https://bastion.test/2026/01/01/post"
    private val postId = UUID.randomUUID()

    private fun fetch(
        status: Int = 200,
        body: String = "<a href=\"$targetUrl\">link</a>",
        contentType: String = "text/html",
    ) = SourceFetch(status, sourceUrl, contentType, body)

    @Test
    fun `verifies a linking html source and stores the analysis`() {
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
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch(status = 410, body = ""))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markDeleted(sourceUrl, postId)
    }

    @Test
    fun `rejects the webmention when the source does not link`() {
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch(body = "<p>no link</p>"))

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markRejected(sourceUrl, postId, "source does not link to the target")
    }

    @Test
    fun `flags the webmention when the source is unreachable`() {
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(
            SourceFetch(0, sourceUrl, null, "", error = "connection refused"),
        )

        receiver.verify(sourceUrl, targetUrl, postId)

        verify(notificationService).markError(sourceUrl, postId, "connection refused")
    }

    @Test
    fun `verifies a source whose document has no microformats as a plain mention`() {
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
}
