package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.service.post.CreateService
import dev.jacobandersen.bastion.micropub.service.post.DeleteService
import dev.jacobandersen.bastion.micropub.service.post.UpdateService
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "bastion.micropub.syndication.targets[0].uid=bridgy",
        "bastion.micropub.syndication.targets[0].name=Bridgy",
        "bastion.micropub.syndication.targets[0].endpoint=https://syndication.example/micropub",
        "bastion.micropub.syndication.targets[0].token=secret",
        "bastion.micropub.syndication.targets[0].actions[0]=create",
        "bastion.micropub.syndication.targets[0].actions[1]=delete",
        "bastion.micropub.syndication.targets[1].uid=delete-only",
        "bastion.micropub.syndication.targets[1].name=Delete Only",
        "bastion.micropub.syndication.targets[1].endpoint=https://syndication.example/delete",
        "bastion.micropub.syndication.targets[1].actions[0]=delete",
    ],
)
@WithMockUser(authorities = ["CREATE", "UPDATE", "DELETE", "UNDELETE"])
class SyndicationIntegrationTest {
    @Autowired
    lateinit var createService: CreateService

    @Autowired
    lateinit var updateService: UpdateService

    @Autowired
    lateinit var deleteService: DeleteService

    @Autowired
    lateinit var syndicationService: SyndicationService

    @Autowired
    lateinit var postService: PostService

    @Autowired
    lateinit var postSyndicationService: PostSyndicationService

    @Autowired
    lateinit var urlService: UrlService

    @MockitoBean
    lateinit var jobScheduler: JobScheduler

    @MockitoBean
    lateinit var httpClient: SyndicationHttpClient

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"

    private fun createPayload(propertiesJson: String): MicropubPayload.Json {
        val root = mapper.readTree("""{"type": ["h-entry"], "properties": $propertiesJson}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    private fun updateJson(
        url: String,
        replaceJson: String,
    ): MicropubPayload.Json {
        val root = mapper.readTree("""{"url": "$url", "replace": {$replaceJson}}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    private fun syndicateTo(uid: String): String = """"mp-syndicate-to": ["$uid"]"""

    private fun createPublicPost(slug: String): Post {
        val response =
            createService.create(
                createPayload(
                    """{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}}""",
                ),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        return postService.findBySlug(slug)!!
    }

    private fun syndicateAndRunCreate(post: Post) {
        syndicationService.runCreateJob(post.id, "bridgy")
    }

    private fun record(post: Post): PostSyndicationEntity = postSyndicationService.findByPostId(post.id).single()

    @Test
    fun `create syndicates to a target supporting create`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))

        val post = createPublicPost(uniqueSlug("syndicate"))

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
        val before = record(post)
        assertEquals("bridgy", before.targetUid)
        assertEquals(null, before.syndicatedUrl)

        syndicationService.runCreateJob(post.id, "bridgy")

        assertEquals("https://brid.gy/syndicated", record(post).syndicatedUrl)
    }

    @Test
    fun `create skips a target that does not support create`() {
        val slug = uniqueSlug("skip")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("delete-only")}}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertTrue(postSyndicationService.findByPostId(post.id).isEmpty())
        verify(jobScheduler, never()).enqueue<SyndicationService>(any())
    }

    @Test
    fun `create without syndication targets records nothing`() {
        val slug = uniqueSlug("nosynd")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"]}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertTrue(postSyndicationService.findByPostId(post.id).isEmpty())
        verify(jobScheduler, never()).enqueue<SyndicationService>(any())
    }

    @Test
    fun `create retains requested targets for a draft without dispatching`() {
        val slug = uniqueSlug("draft")
        val response =
            createService.create(
                createPayload(
                    """
                    {"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}, "post-status": ["draft"]}
                    """.trimIndent(),
                ),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        val retained = record(post)
        assertEquals("bridgy", retained.targetUid)
        assertEquals(null, retained.syndicatedUrl)
        verify(jobScheduler, never()).enqueue<SyndicationService>(any())
    }

    @Test
    fun `create retains requested targets for a private post without dispatching`() {
        val slug = uniqueSlug("private")
        val response =
            createService.create(
                createPayload(
                    """
                    {"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}, "visibility": ["private"]}
                    """.trimIndent(),
                ),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        val retained = record(post)
        assertEquals("bridgy", retained.targetUid)
        assertEquals(null, retained.syndicatedUrl)
        verify(jobScheduler, never()).enqueue<SyndicationService>(any())
    }

    @Test
    fun `publishing a draft dispatches create to the retained target`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))

        val slug = uniqueSlug("publish-draft")
        createService.create(
            createPayload(
                """
                {"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}, "post-status": ["draft"]}
                """.trimIndent(),
            ),
            null,
        )
        val post = postService.findBySlug(slug)!!
        verify(jobScheduler, never()).enqueue<SyndicationService>(any())

        val response =
            updateService.update(updateJson(urlService.generatePostUrl(post), """"post-status": ["published"]"""))
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())

        syndicationService.runCreateJob(post.id, "bridgy")
        assertEquals("https://brid.gy/syndicated", record(post).syndicatedUrl)
    }

    @Test
    fun `delete dispatches delete to recorded targets that hold a copy`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))
        `when`(httpClient.sendDelete(any(), any()))
            .thenReturn(SyndicationSendResult.Success(204, null))

        val post = createPublicPost(uniqueSlug("syndelete"))
        syndicationService.runCreateJob(post.id, "bridgy")
        assertEquals("https://brid.gy/syndicated", record(post).syndicatedUrl)
        reset(jobScheduler)

        val url = urlService.generatePostUrl(post)
        val response =
            deleteService.delete(
                MicropubPayload.Json(mapper.createObjectNode().put("url", url)),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
        assertDoesNotThrow { syndicationService.runDeleteJob(post.id, "bridgy", url) }
        verify(httpClient).sendDelete(any(), eq(url))
    }

    @Test
    fun `demoting a public post dispatches delete to retract the copy`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))
        `when`(httpClient.sendDelete(any(), any()))
            .thenReturn(SyndicationSendResult.Success(204, null))

        val post = createPublicPost(uniqueSlug("demote"))
        syndicationService.runCreateJob(post.id, "bridgy")
        reset(jobScheduler)

        val url = urlService.generatePostUrl(post)
        val response =
            updateService.update(updateJson(url, """"post-status": ["draft"]"""))
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
        assertDoesNotThrow { syndicationService.runDeleteJob(post.id, "bridgy", url) }
        verify(httpClient).sendDelete(any(), eq(url))
    }

    @Test
    fun `deleting a previously syndicated post while non-public still dispatches delete`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))
        `when`(httpClient.sendDelete(any(), any()))
            .thenReturn(SyndicationSendResult.Success(204, null))

        val post = createPublicPost(uniqueSlug("delete-demoted"))
        syndicationService.runCreateJob(post.id, "bridgy")
        val url = urlService.generatePostUrl(post)
        updateService.update(updateJson(url, """"post-status": ["draft"]"""))
        reset(jobScheduler)

        val deleteResponse =
            deleteService.delete(
                MicropubPayload.Json(mapper.createObjectNode().put("url", url)),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, deleteResponse)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
    }

    @Test
    fun `undelete dispatches create re-syndication to recorded targets`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))
        `when`(httpClient.sendDelete(any(), any()))
            .thenReturn(SyndicationSendResult.Success(204, null))

        val post = createPublicPost(uniqueSlug("undelete"))
        syndicationService.runCreateJob(post.id, "bridgy")
        val url = urlService.generatePostUrl(post)
        deleteService.delete(MicropubPayload.Json(mapper.createObjectNode().put("url", url)))
        reset(jobScheduler)

        val undeleteResponse =
            deleteService.undelete(
                MicropubPayload.Json(mapper.createObjectNode().put("url", url)),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, undeleteResponse)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())
        syndicationService.runCreateJob(post.id, "bridgy")
        assertEquals("https://brid.gy/syndicated", record(post).syndicatedUrl)
    }

    @Test
    fun `renaming a slug re-bases the copy on the recorded target`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/rebased"))
        `when`(httpClient.sendDelete(any(), any()))
            .thenReturn(SyndicationSendResult.Success(204, null))

        val post = createPublicPost(uniqueSlug("rename"))
        val previousUrl = urlService.generatePostUrl(post)
        syndicationService.runCreateJob(post.id, "bridgy")
        reset(jobScheduler)

        val newSlug = uniqueSlug("renamed")
        val response =
            updateService.update(updateJson(previousUrl, """"mp-slug": ["$newSlug"]"""))
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())

        val renamed = postService.findBySlug(newSlug)!!
        syndicationService.runRebaseJob(renamed.id, "bridgy", previousUrl)

        verify(httpClient).sendDelete(any(), eq(previousUrl))
        assertEquals("https://brid.gy/rebased", record(renamed).syndicatedUrl)
    }

    @Test
    fun `a queued create job does not transmit content when the post is demoted before it runs`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))

        val slug = uniqueSlug("create-race")
        createService.create(
            createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}}"""),
            null,
        )
        val post = postService.findBySlug(slug)!!
        verify(jobScheduler, times(1)).enqueue<SyndicationService>(any())

        val url = urlService.generatePostUrl(post)
        updateService.update(updateJson(url, """"post-status": ["draft"]"""))

        syndicationService.runCreateJob(post.id, "bridgy")

        verify(httpClient, never()).sendCreate(any(), any())
        assertEquals(null, record(post).syndicatedUrl)
    }

    @Test
    fun `a queued create job does not transmit content when the post is deleted before it runs`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))

        val slug = uniqueSlug("create-delete-race")
        createService.create(
            createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}}"""),
            null,
        )
        val post = postService.findBySlug(slug)!!
        val url = urlService.generatePostUrl(post)
        deleteService.delete(MicropubPayload.Json(mapper.createObjectNode().put("url", url)))

        syndicationService.runCreateJob(post.id, "bridgy")

        verify(httpClient, never()).sendCreate(any(), any())
        assertEquals(null, record(post).syndicatedUrl)
    }

    @Test
    fun `syndication failure does not fail the create and is logged without retry`() {
        `when`(httpClient.sendCreate(any(), any()))
            .thenThrow(RuntimeException("downstream exploded"))

        val slug = uniqueSlug("fail")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], ${syndicateTo("bridgy")}}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertDoesNotThrow { syndicationService.runCreateJob(post.id, "bridgy") }
        assertEquals(null, postSyndicationService.findByPostId(post.id).single().syndicatedUrl)
    }
}
