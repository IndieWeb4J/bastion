package dev.jacobandersen.bastion.webmention.data.service

import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.entity.ReceivedWebmentionEntity
import dev.jacobandersen.bastion.webmention.data.repository.ReceivedWebmentionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.time.Instant
import java.util.UUID

class ReceivedWebmentionServiceTest {
    private val repository = mock(ReceivedWebmentionRepository::class.java)
    private val service = ReceivedWebmentionService(repository)

    init {
        `when`(repository.save(any())).thenAnswer {
            val entity = it.arguments[0] as ReceivedWebmentionEntity
            if (entity.id == null) entity.id = UUID.randomUUID()
            entity
        }
    }

    private val sourceUrl = "https://source.example/reply"
    private val targetUrl = "https://bastion.test/2026/01/01/post"
    private val postId = UUID.randomUUID()

    private fun entity(
        state: ReceivedWebmentionState = ReceivedWebmentionState.PENDING,
        interaction: WebmentionInteraction? = null,
        contentText: String? = null,
        wasVerified: Boolean = false,
    ) = ReceivedWebmentionEntity(
        postId = postId,
        sourceUrl = sourceUrl,
        targetUrl = targetUrl,
        state = state,
        interaction = interaction,
        authorName = null,
        authorUrl = null,
        authorPhoto = null,
        contentText = contentText,
        contentHtml = null,
        rawMf2 = null,
        lastError = null,
        firstSeenAt = Instant.now(),
        verifiedAt = null,
        updatedAtUtc = Instant.now(),
        wasVerified = wasVerified,
    )

    @Test
    fun `ensurePending creates a new pending record`() {
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(null)

        service.ensurePending(sourceUrl, targetUrl, postId)

        val captor = ArgumentCaptor.forClass(ReceivedWebmentionEntity::class.java)
        verify(repository).save(captor.capture())
        val saved = captor.value
        assertEquals(ReceivedWebmentionState.PENDING, saved.state)
        assertEquals(targetUrl, saved.targetUrl)
        assertNotNull(saved.firstSeenAt)
    }

    @Test
    fun `ensurePending reopens a previously rejected record`() {
        val existing = entity(state = ReceivedWebmentionState.REJECTED, interaction = WebmentionInteraction.LIKE, contentText = "old")
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)

        service.ensurePending(sourceUrl, targetUrl, postId)

        assertEquals(ReceivedWebmentionState.PENDING, existing.state)
        assertNull(existing.lastError)
        assertNull(existing.verifiedAt)
        verify(repository, times(1)).save(existing)
    }

    @Test
    fun `ensurePending leaves a pending record untouched`() {
        val existing = entity(state = ReceivedWebmentionState.PENDING)
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)
        existing.id = UUID.randomUUID()

        val result = service.ensurePending(sourceUrl, targetUrl, postId)

        assertEquals(existing.id, result.id)
        verify(repository, times(0)).save(existing)
    }

    @Test
    fun `markVerified stores the analysis`() {
        val existing = entity()
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)

        service.markVerified(
            sourceUrl,
            postId,
            ReceivedWebmentionAnalysis(
                interaction = WebmentionInteraction.REPLY,
                primary = null,
                authorName = "Jane",
                authorUrl = "https://jane.example",
                contentText = "Nice post",
                contentHtml = "<p>Nice post</p>",
            ),
        )

        assertEquals(ReceivedWebmentionState.VERIFIED, existing.state)
        assertEquals(WebmentionInteraction.REPLY, existing.interaction)
        assertEquals("Jane", existing.authorName)
        assertEquals("https://jane.example", existing.authorUrl)
        assertEquals("Nice post", existing.contentText)
        assertEquals("<p>Nice post</p>", existing.contentHtml)
        assertNotNull(existing.verifiedAt)
        assertNull(existing.lastError)
        assertTrue(existing.wasVerified)
    }

    @Test
    fun `markVerified throws when the record is missing`() {
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(null)

        assertThrows(IllegalStateException::class.java) {
            service.markVerified(sourceUrl, postId, ReceivedWebmentionAnalysis(WebmentionInteraction.MENTION, null))
        }
    }

    @Test
    fun `markRejected records the reason`() {
        val existing = entity()
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)

        service.markRejected(sourceUrl, postId, "source does not link to the target")

        assertEquals(ReceivedWebmentionState.REJECTED, existing.state)
        assertEquals("source does not link to the target", existing.lastError)
    }

    @Test
    fun `markDeleted clears extracted content but keeps the was-verified marker`() {
        val existing =
            entity(
                state = ReceivedWebmentionState.VERIFIED,
                interaction = WebmentionInteraction.REPLY,
                contentText = "Nice post",
                wasVerified = true,
            )
        existing.authorName = "Jane"
        existing.rawMf2 =
            dev.jacobandersen.bastion.microformats2.Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mapOf(
                        "content" to
                            listOf(
                                dev.jacobandersen.bastion.microformats2.Mf2Value
                                    .String("Nice post"),
                            ),
                    ),
            )
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)

        service.markDeleted(sourceUrl, postId)

        assertEquals(ReceivedWebmentionState.DELETED, existing.state)
        assertNull(existing.interaction)
        assertNull(existing.authorName)
        assertNull(existing.contentText)
        assertNull(existing.rawMf2)
        assertTrue(existing.wasVerified)
    }

    @Test
    fun `ensurePending reopens a deleted record without losing the was-verified marker`() {
        val existing =
            entity(
                state = ReceivedWebmentionState.DELETED,
                interaction = null,
                contentText = null,
                wasVerified = true,
            )
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)

        val reopened = service.ensurePending(sourceUrl, targetUrl, postId)

        assertEquals(ReceivedWebmentionState.PENDING, reopened.state)
        assertTrue(reopened.wasVerified)
    }

    @Test
    fun `markError records the reason`() {
        val existing = entity()
        `when`(repository.findBySourceUrlAndPostId(sourceUrl, postId)).thenReturn(existing)

        service.markError(sourceUrl, postId, "timeout")

        assertEquals(ReceivedWebmentionState.ERROR, existing.state)
        assertEquals("timeout", existing.lastError)
    }

    @Test
    fun `byPost maps all records`() {
        val saved = entity(state = ReceivedWebmentionState.VERIFIED)
        saved.id = UUID.randomUUID()
        `when`(repository.findByPostId(postId)).thenReturn(listOf(saved))

        val result = service.byPost(postId)

        assertEquals(1, result.size)
        assertEquals(saved.id, result[0].id)
    }
}
