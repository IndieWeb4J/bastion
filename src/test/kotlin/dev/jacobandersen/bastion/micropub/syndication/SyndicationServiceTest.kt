package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.req.MicropubUpdatePayload
import dev.jacobandersen.bastion.url.UrlService
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

class SyndicationServiceTest {
    private val jobScheduler = mock(JobScheduler::class.java)
    private val config = mock(SyndicationConfig::class.java)
    private val httpClient = mock(SyndicationHttpClient::class.java)
    private val postSyndicationService = mock(PostSyndicationService::class.java)
    private val postService = mock(PostService::class.java)
    private val urlService = mock(UrlService::class.java)

    private val service =
        SyndicationService(jobScheduler, config, httpClient, postSyndicationService, postService, urlService)

    private val postId = UUID.randomUUID()

    private val post =
        Post(
            id = postId,
            slug = "slug",
            status = PostStatus.PUBLISHED,
            visibility = PostVisibility.PUBLIC,
            type = "h-entry",
            post =
                Mf2Object(
                    type = listOf("h-entry"),
                    properties = mapOf("content" to listOf(Mf2Value.String("body"))),
                    children = null,
                ),
        )

    private val createTarget =
        SyndicationConfig.Target(
            uid = "bridgy",
            name = "Bridgy",
            endpoint = "https://brid.gy/micropub",
            actions = setOf(SyndicationAction.CREATE, SyndicationAction.DELETE),
        )

    private val deleteOnlyTarget =
        SyndicationConfig.Target(
            uid = "delete-only",
            name = "Delete Only",
            endpoint = "https://example.com/micropub",
            actions = setOf(SyndicationAction.DELETE),
        )

    private val updateTarget =
        SyndicationConfig.Target(
            uid = "update-capable",
            name = "Update Capable",
            endpoint = "https://example.com/micropub",
            actions = setOf(SyndicationAction.UPDATE),
        )

    private val updatePayload =
        MicropubUpdatePayload(
            url = "https://bastion.test/2026/01/01/slug",
            replacements = mapOf("name" to listOf(Mf2Value.String("Updated"))),
            additions = null,
            removals = null,
        )

    private val serializedUpdate =
        SyndicationUpdate(
            replace = """{"name":["Updated"]}""",
            add = null,
            delete = null,
        )

    @Test
    fun `syndicateCreated records and enqueues for targets supporting create`() {
        `when`(config.targetsSupporting(listOf("bridgy"), SyndicationAction.CREATE)).thenReturn(listOf(createTarget))

        service.syndicateCreated(post, listOf("bridgy"))

        verify(postSyndicationService, times(1)).record(postId, "bridgy")
        verify(jobScheduler, times(1)).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateCreated skips targets without create support`() {
        `when`(config.targetsSupporting(listOf("delete-only"), SyndicationAction.CREATE)).thenReturn(emptyList())

        service.syndicateCreated(post, listOf("delete-only"))

        verify(postSyndicationService, never()).record(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `retainSyndicationTargets records create-capable targets without enqueueing`() {
        `when`(config.targetsSupporting(listOf("bridgy"), SyndicationAction.CREATE)).thenReturn(listOf(createTarget))

        service.retainSyndicationTargets(post, listOf("bridgy"))

        verify(postSyndicationService, times(1)).record(postId, "bridgy")
        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `retainSyndicationTargets skips targets without create support`() {
        `when`(config.targetsSupporting(listOf("delete-only"), SyndicationAction.CREATE)).thenReturn(emptyList())

        service.retainSyndicationTargets(post, listOf("delete-only"))

        verify(postSyndicationService, never()).record(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateDeleted enqueues only for recorded targets supporting delete that hold a copy`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(
                listOf(
                    PostSyndicationEntity(postId = postId, targetUid = "bridgy").apply {
                        syndicatedUrl = "https://brid.gy/syndicated"
                    },
                    PostSyndicationEntity(postId = postId, targetUid = "delete-only").apply {
                        syndicatedUrl = "https://example.com/copy"
                    },
                    PostSyndicationEntity(postId = postId, targetUid = "missing").apply {
                        syndicatedUrl = "https://example.com/missing"
                    },
                ),
            )
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(config.targetByUid("delete-only")).thenReturn(deleteOnlyTarget)
        `when`(config.targetByUid("missing")).thenReturn(null)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")

        service.syndicateDeleted(post)

        verify(jobScheduler, times(2)).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateDeleted does not enqueue for recorded targets without a syndication outcome`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(listOf(PostSyndicationEntity(postId = postId, targetUid = "bridgy")))
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")

        service.syndicateDeleted(post)

        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateDeleted does nothing when no records exist`() {
        `when`(postSyndicationService.findByPostId(postId)).thenReturn(emptyList())
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")

        service.syndicateDeleted(post)

        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicatePublished enqueues create for recorded targets supporting create`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(
                listOf(
                    PostSyndicationEntity(postId = postId, targetUid = "bridgy"),
                    PostSyndicationEntity(postId = postId, targetUid = "delete-only"),
                    PostSyndicationEntity(postId = postId, targetUid = "missing"),
                ),
            )
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(config.targetByUid("delete-only")).thenReturn(deleteOnlyTarget)
        `when`(config.targetByUid("missing")).thenReturn(null)

        service.syndicatePublished(post)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicatePublished does nothing when no create-capable records exist`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(listOf(PostSyndicationEntity(postId = postId, targetUid = "delete-only")))
        `when`(config.targetByUid("delete-only")).thenReturn(deleteOnlyTarget)

        service.syndicatePublished(post)

        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateUndeleted enqueues create for recorded targets supporting create`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(listOf(PostSyndicationEntity(postId = postId, targetUid = "bridgy")))
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)

        service.syndicateUndeleted(post)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateRebased enqueues rebase for recorded targets supporting create and delete`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(
                listOf(
                    PostSyndicationEntity(postId = postId, targetUid = "bridgy"),
                    PostSyndicationEntity(postId = postId, targetUid = "delete-only"),
                    PostSyndicationEntity(postId = postId, targetUid = "missing"),
                ),
            )
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(config.targetByUid("delete-only")).thenReturn(deleteOnlyTarget)
        `when`(config.targetByUid("missing")).thenReturn(null)

        service.syndicateRebased(post, "https://bastion.test/2026/01/01/old-slug")

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateUpdated enqueues only for recorded targets supporting update`() {
        `when`(postSyndicationService.findByPostId(postId))
            .thenReturn(
                listOf(
                    PostSyndicationEntity(postId = postId, targetUid = "bridgy"),
                    PostSyndicationEntity(postId = postId, targetUid = "update-capable"),
                    PostSyndicationEntity(postId = postId, targetUid = "missing"),
                ),
            )
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(config.targetByUid("update-capable")).thenReturn(updateTarget)
        `when`(config.targetByUid("missing")).thenReturn(null)
        `when`(httpClient.serializeUpdate(updatePayload)).thenReturn(serializedUpdate)

        service.syndicateUpdated(post, updatePayload)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(org.mockito.kotlin.any())
    }

    @Test
    fun `syndicateUpdated does nothing when no records exist`() {
        `when`(postSyndicationService.findByPostId(postId)).thenReturn(emptyList())

        service.syndicateUpdated(post, updatePayload)

        verify(jobScheduler, never()).enqueue<SyndicationService>(org.mockito.kotlin.any())
        verify(httpClient, never()).serializeUpdate(org.mockito.kotlin.any())
    }

    @Test
    fun `runCreateJob does not throw when the http client fails`() {
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        `when`(httpClient.sendCreate(createTarget, post.post)).thenThrow(RuntimeException("boom"))

        assertDoesNotThrow { service.runCreateJob(postId, "bridgy") }

        verify(postSyndicationService, never()).recordOutcome(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `runCreateJob does not send the content when the post was demoted before the job ran`() {
        `when`(postService.findById(postId)).thenReturn(post.copy(status = PostStatus.DRAFT))
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)

        service.runCreateJob(postId, "bridgy")

        verify(httpClient, never()).sendCreate(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(postSyndicationService, never()).recordOutcome(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `runCreateJob does not send the content when the post was deleted before the job ran`() {
        `when`(postService.findById(postId)).thenReturn(post.copy(deleted = true))
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)

        service.runCreateJob(postId, "bridgy")

        verify(httpClient, never()).sendCreate(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(postSyndicationService, never()).recordOutcome(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `runRebaseJob retracts the old copy but does not re-create when the post is no longer public`() {
        `when`(postService.findById(postId)).thenReturn(post.copy(status = PostStatus.DRAFT))
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(httpClient.sendDelete(createTarget, "https://bastion.test/2026/01/01/old-slug"))
            .thenReturn(SyndicationSendResult.Success(204, null))

        service.runRebaseJob(postId, "bridgy", "https://bastion.test/2026/01/01/old-slug")

        verify(httpClient, times(1)).sendDelete(createTarget, "https://bastion.test/2026/01/01/old-slug")
        verify(httpClient, never()).sendCreate(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        verify(postSyndicationService, never()).recordOutcome(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `runCreateJob records the syndicated url on success`() {
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        `when`(httpClient.sendCreate(createTarget, post.post))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))

        service.runCreateJob(postId, "bridgy")

        verify(postSyndicationService, times(1)).recordOutcome(postId, "bridgy", "https://brid.gy/syndicated")
    }

    @Test
    fun `runDeleteJob does not throw when the http client fails`() {
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(
            httpClient.sendDelete(
                createTarget,
                "https://bastion.test/2026/01/01/slug",
            ),
        ).thenThrow(RuntimeException("boom"))

        assertDoesNotThrow { service.runDeleteJob(postId, "bridgy", "https://bastion.test/2026/01/01/slug") }
    }

    @Test
    fun `runRebaseJob deletes the old copy and records the re-created copy on success`() {
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        `when`(httpClient.sendDelete(createTarget, "https://bastion.test/2026/01/01/old-slug"))
            .thenReturn(SyndicationSendResult.Success(204, null))
        `when`(httpClient.sendCreate(createTarget, post.post))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/rebased"))

        service.runRebaseJob(postId, "bridgy", "https://bastion.test/2026/01/01/old-slug")

        verify(httpClient, times(1)).sendDelete(createTarget, "https://bastion.test/2026/01/01/old-slug")
        verify(httpClient, times(1)).sendCreate(createTarget, post.post)
        verify(postSyndicationService, times(1)).recordOutcome(postId, "bridgy", "https://brid.gy/rebased")
    }

    @Test
    fun `runRebaseJob does not throw when the http client fails`() {
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(config.targetByUid("bridgy")).thenReturn(createTarget)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        `when`(httpClient.sendDelete(createTarget, "https://bastion.test/2026/01/01/old-slug")).thenThrow(
            RuntimeException("boom"),
        )

        assertDoesNotThrow { service.runRebaseJob(postId, "bridgy", "https://bastion.test/2026/01/01/old-slug") }

        verify(postSyndicationService, never()).recordOutcome(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }

    @Test
    fun `runUpdateJob does not throw when the http client fails`() {
        `when`(postService.findById(postId)).thenReturn(post)
        `when`(config.targetByUid("update-capable")).thenReturn(updateTarget)
        `when`(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        `when`(httpClient.sendUpdate(updateTarget, "https://bastion.test/2026/01/01/slug", serializedUpdate))
            .thenThrow(RuntimeException("boom"))

        assertDoesNotThrow { service.runUpdateJob(postId, "update-capable", serializedUpdate) }
    }
}
