package dev.jacobandersen.bastion.micropub.syndication

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.service.post.CreateService
import dev.jacobandersen.bastion.micropub.service.post.DeleteService
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
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
@WithMockUser(authorities = ["CREATE", "DELETE", "UNDELETE"])
class SyndicationIntegrationTest {
    @Autowired
    lateinit var createService: CreateService

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
    lateinit var httpClient: SyndicationHttpClient

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"

    private fun createPayload(propertiesJson: String): MicropubPayload.Json {
        val root = mapper.readTree("""{"type": ["h-entry"], "properties": $propertiesJson}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    @Test
    fun `create syndicates to a target supporting create`() {
        `when`(httpClient.sendCreate(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(SyndicationSendResult.Success(201, "https://brid.gy/syndicated"))

        val slug = uniqueSlug("syndicate")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], "mp-syndicate-to": ["bridgy"]}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        val records = postSyndicationService.findByPostId(post.id)
        assertEquals(1, records.size)
        assertEquals("bridgy", records.single().targetUid)
        assertEquals(null, records.single().syndicatedUrl)

        syndicationService.runCreateJob(post.id, "bridgy")

        val updated = postSyndicationService.findByPostId(post.id).single()
        assertEquals("https://brid.gy/syndicated", updated.syndicatedUrl)
    }

    @Test
    fun `create skips a target that does not support create`() {
        val slug = uniqueSlug("skip")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], "mp-syndicate-to": ["delete-only"]}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertTrue(postSyndicationService.findByPostId(post.id).isEmpty())
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
    }

    @Test
    fun `create does not syndicate a draft`() {
        val slug = uniqueSlug("draft")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], "mp-syndicate-to": ["bridgy"], "post-status": ["draft"]}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertTrue(postSyndicationService.findByPostId(post.id).isEmpty())
    }

    @Test
    fun `create does not syndicate a private post`() {
        val slug = uniqueSlug("private")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], "mp-syndicate-to": ["bridgy"], "visibility": ["private"]}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertTrue(postSyndicationService.findByPostId(post.id).isEmpty())
    }

    @Test
    fun `delete syndicates to recorded targets that support delete`() {
        `when`(httpClient.sendDelete(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(SyndicationSendResult.Success(204, null))

        val slug = uniqueSlug("syndelete")
        createService.create(
            createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], "mp-syndicate-to": ["bridgy"]}"""),
            null,
        )
        val post = postService.findBySlug(slug)!!
        assertEquals(1, postSyndicationService.findByPostId(post.id).size)

        val response =
            deleteService.delete(
                MicropubPayload.Json(mapper.createObjectNode().put("url", urlService.generatePostUrl(post))),
            )
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        assertDoesNotThrow { syndicationService.runDeleteJob(post.id, "bridgy") }
    }

    @Test
    fun `syndication failure does not fail the create and is logged without retry`() {
        `when`(httpClient.sendCreate(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenThrow(RuntimeException("downstream exploded"))

        val slug = uniqueSlug("fail")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": ["Body"], "mp-slug": ["$slug"], "mp-syndicate-to": ["bridgy"]}"""),
                null,
            )
        assertInstanceOf(ApiResponse.Success.Created::class.java, response)

        val post = postService.findBySlug(slug)!!
        assertDoesNotThrow { syndicationService.runCreateJob(post.id, "bridgy") }
        assertEquals(null, postSyndicationService.findByPostId(post.id).single().syndicatedUrl)
    }
}
