package dev.jacobandersen.bastion.webmention.salmention.data.service

import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.salmention.data.entity.SalmentionResponseEntity
import dev.jacobandersen.bastion.webmention.salmention.data.repository.SalmentionResponseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.time.Instant
import java.util.UUID

class SalmentionResponseServiceTest {
    private val repository = mock(SalmentionResponseRepository::class.java)
    private val service = SalmentionResponseService(repository)

    private val sourceUrl = "https://source.example/reply"
    private val receivedWebmentionId = UUID.randomUUID()
    private val responseUrl = "https://carol.example/reply"

    private val analysis =
        ReceivedWebmentionAnalysis(
            interaction = WebmentionInteraction.REPLY,
            primary = null,
            authorName = "Carol",
            contentText = "well said",
        )

    init {
        `when`(repository.save(any())).thenAnswer {
            val entity = it.arguments[0] as SalmentionResponseEntity
            if (entity.id == null) entity.id = UUID.randomUUID()
            entity
        }
    }

    @Test
    fun `ingest stores a new nested response`() {
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)).thenReturn(null)

        val result = service.ingest(sourceUrl, receivedWebmentionId, responseUrl, analysis)

        val captor = ArgumentCaptor.forClass(SalmentionResponseEntity::class.java)
        verify(repository).save(captor.capture())
        assertEquals(WebmentionInteraction.REPLY, captor.value.interaction)
        assertEquals("Carol", captor.value.authorName)
        assertEquals(responseUrl, result?.responseUrl)
    }

    @Test
    fun `ingest is a no-op for an already seen response on the same received webmention`() {
        val existing =
            SalmentionResponseEntity(
                receivedWebmentionId = receivedWebmentionId,
                sourceUrl = sourceUrl,
                responseUrl = responseUrl,
                interaction = WebmentionInteraction.REPLY,
                authorName = null,
                authorUrl = null,
                authorPhoto = null,
                contentText = null,
                contentHtml = null,
                rawMf2 = null,
                firstSeenAt = Instant.now(),
                updatedAtUtc = Instant.now(),
            )
        existing.id = UUID.randomUUID()
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)).thenReturn(existing)

        val result = service.ingest(sourceUrl, receivedWebmentionId, responseUrl, analysis)

        assertNull(result)
        verify(repository, times(0)).save(any())
    }

    @Test
    fun `ingest stores the same response for a different received webmention`() {
        val otherReceivedWebmentionId = UUID.randomUUID()
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)).thenReturn(null)
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(otherReceivedWebmentionId, responseUrl)).thenReturn(null)

        service.ingest(sourceUrl, receivedWebmentionId, responseUrl, analysis)
        val result = service.ingest(sourceUrl, otherReceivedWebmentionId, responseUrl, analysis)

        verify(repository, times(2)).save(any())
        assertEquals(responseUrl, result?.responseUrl)
        assertEquals(otherReceivedWebmentionId, result?.receivedWebmentionId)
    }

    @Test
    fun `responseUrlsByReceivedWebmention returns the stored response urls`() {
        val entity =
            SalmentionResponseEntity(
                receivedWebmentionId = receivedWebmentionId,
                sourceUrl = sourceUrl,
                responseUrl = responseUrl,
                interaction = WebmentionInteraction.REPLY,
                authorName = null,
                authorUrl = null,
                authorPhoto = null,
                contentText = null,
                contentHtml = null,
                rawMf2 = null,
                firstSeenAt = Instant.now(),
                updatedAtUtc = Instant.now(),
            )
        `when`(repository.findByReceivedWebmentionId(receivedWebmentionId)).thenReturn(listOf(entity))

        assertEquals(setOf(responseUrl), service.responseUrlsByReceivedWebmention(receivedWebmentionId))
    }

    @Test
    fun `retireByReceivedWebmention delegates to the repository`() {
        `when`(repository.deleteByReceivedWebmentionId(receivedWebmentionId)).thenReturn(2)

        assertEquals(2, service.retireByReceivedWebmention(receivedWebmentionId))
    }
}
