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
import org.mockito.Mockito.never
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
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)).thenReturn(entity())

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

    private fun entity(
        receivedId: UUID = receivedWebmentionId,
        url: String = responseUrl,
    ): SalmentionResponseEntity {
        val entity =
            SalmentionResponseEntity(
                receivedWebmentionId = receivedId,
                sourceUrl = sourceUrl,
                responseUrl = url,
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
        entity.id = UUID.randomUUID()
        return entity
    }

    @Test
    fun `responseUrls are exposed via byReceivedWebmention`() {
        `when`(repository.findByReceivedWebmentionId(receivedWebmentionId)).thenReturn(listOf(entity()))

        assertEquals(listOf(responseUrl), service.byReceivedWebmention(receivedWebmentionId).map { it.responseUrl })
    }

    @Test
    fun `retireByReceivedWebmention delegates to the repository`() {
        `when`(repository.deleteByReceivedWebmentionId(receivedWebmentionId)).thenReturn(2)

        assertEquals(2, service.retireByReceivedWebmention(receivedWebmentionId))
    }

    @Test
    fun `refresh rewrites the snapshot of an existing response`() {
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)).thenReturn(entity())
        val updated =
            ReceivedWebmentionAnalysis(
                interaction = WebmentionInteraction.MENTION,
                primary = null,
                authorName = "Carol edited",
                contentText = "changed my mind",
            )

        val result = service.refresh(receivedWebmentionId, responseUrl, updated)

        val captor = ArgumentCaptor.forClass(SalmentionResponseEntity::class.java)
        verify(repository).save(captor.capture())
        assertEquals(WebmentionInteraction.MENTION, captor.value.interaction)
        assertEquals("Carol edited", captor.value.authorName)
        assertEquals("changed my mind", captor.value.contentText)
        assertEquals(responseUrl, result?.responseUrl)
    }

    @Test
    fun `refresh returns null when no row exists for the pair`() {
        `when`(repository.findByReceivedWebmentionIdAndResponseUrl(receivedWebmentionId, responseUrl)).thenReturn(null)

        val result = service.refresh(receivedWebmentionId, responseUrl, analysis)

        assertNull(result)
        verify(repository, never()).save(any())
    }

    @Test
    fun `retireRemoved deletes stored responses no longer present on the source`() {
        val carol = entity(url = "https://carol.example/reply")
        val dave = entity(url = "https://dave.example/reply")
        `when`(repository.findByReceivedWebmentionId(receivedWebmentionId)).thenReturn(listOf(carol, dave))

        val removed = service.retireRemoved(receivedWebmentionId, setOf("https://carol.example/reply"))

        assertEquals(1, removed)
        verify(repository).deleteAll(listOf(dave))
    }

    @Test
    fun `retireRemoved keeps everything when all stored responses are still present`() {
        val carol = entity(url = "https://carol.example/reply")
        `when`(repository.findByReceivedWebmentionId(receivedWebmentionId)).thenReturn(listOf(carol))

        val removed = service.retireRemoved(receivedWebmentionId, setOf("https://carol.example/reply"))

        assertEquals(0, removed)
        verify(repository, never()).deleteAll(any())
    }
}
