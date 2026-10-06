package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.websub.service.WebsubPublisher
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.clearInvocations
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
    ],
)
@WithMockUser(authorities = ["CREATE", "UPDATE", "DELETE", "UNDELETE"])
class WebsubDispatchIntegrationTest {
    @Autowired
    lateinit var createService: CreateService

    @Autowired
    lateinit var updateService: UpdateService

    @Autowired
    lateinit var deleteService: DeleteService

    @MockitoBean
    lateinit var websubPublisher: WebsubPublisher

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"

    private fun createPayload(
        content: String,
        slug: String,
        status: String = "published",
        visibility: String = "public",
    ): MicropubPayload.Json {
        val root =
            mapper.readTree(
                """{"type": ["h-entry"], "properties": {"name": ["$slug"], "content": ["$content"], "mp-slug": ["$slug"], "post-status": ["$status"], "visibility": ["$visibility"]}}""",
            ) as ObjectNode
        return MicropubPayload.Json(root)
    }

    private fun createPublicPost(content: String): String {
        val slug = uniqueSlug("websub")
        val response = createService.create(createPayload(content, slug), null)
        return (response as ApiResponse.Success.Created).location
    }

    private fun updateJson(
        url: String,
        update: String,
    ): MicropubPayload.Json {
        val root = mapper.readTree("""{"url": "$url", $update}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    @Test
    fun createPublishesForPublicPosts() {
        createPublicPost("Hello WebSub")

        verify(websubPublisher, times(1)).publish()
    }

    @Test
    fun createDoesNotPublishForDraftOrPrivatePosts() {
        val draftSlug = uniqueSlug("websub-draft")
        createService.create(createPayload("Draft", draftSlug, status = "draft"), null)
        verify(websubPublisher, never()).publish()

        val privateSlug = uniqueSlug("websub-private")
        createService.create(createPayload("Private", privateSlug, visibility = "private"), null)
        verify(websubPublisher, never()).publish()
    }

    @Test
    fun updatePublishesForPublicPosts() {
        val location = createPublicPost("Before")
        clearInvocations(websubPublisher)

        val response = updateService.update(updateJson(location, """"replace": {"content": ["After"]}"""))
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        verify(websubPublisher, times(1)).publish()
    }

    @Test
    fun updateToPrivateDoesNotPublish() {
        val location = createPublicPost("Before")
        clearInvocations(websubPublisher)

        val response = updateService.update(updateJson(location, """"replace": {"visibility": ["private"]}"""))
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, response)

        verify(websubPublisher, never()).publish()
    }

    @Test
    fun deleteAndUndeletePublish() {
        val location = createPublicPost("Hello WebSub")
        clearInvocations(websubPublisher)

        val deleteResponse = deleteService.delete(MicropubPayload.Json(mapper.createObjectNode().put("url", location)))
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, deleteResponse)

        val undeleteResponse =
            deleteService.undelete(MicropubPayload.Json(mapper.createObjectNode().put("url", location)))
        assertInstanceOf(ApiResponse.Success.NoContent::class.java, undeleteResponse)

        verify(websubPublisher, times(2)).publish()
    }
}
