package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@WithMockUser(authorities = ["DELETE", "UNDELETE"])
class DeleteServiceTest {
    @Autowired
    lateinit var deleteService: DeleteService

    @Autowired
    lateinit var postService: PostService

    @Autowired
    lateinit var urlService: UrlService

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"

    private fun createPost(slug: String): Post {
        val obj =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mutableMapOf(
                        "content" to listOf(Mf2Value.String("Body")),
                    ),
                children = null,
            )
        return postService.create(slug, PostStatus.PUBLISHED, PostVisibility.PUBLIC, false, obj)
    }

    private fun actionPayload(url: String): MicropubPayload.Json =
        MicropubPayload.Json(mapper.createObjectNode().put("url", url) as ObjectNode)

    @Test
    fun deleteReturnsNoContentAndFlagsPost() {
        val post = createPost(uniqueSlug("delete"))

        val response = deleteService.delete(actionPayload(urlService.generatePostUrl(post)))

        assertEquals(ApiResponse.Success.NoContent, response)
        assertTrue(postService.findBySlug(post.slug)!!.deleted)
    }

    @Test
    fun undeleteReturnsNoContentAndClearsFlag() {
        val post = createPost(uniqueSlug("undelete"))

        deleteService.delete(actionPayload(urlService.generatePostUrl(post)))
        assertTrue(postService.findBySlug(post.slug)!!.deleted)

        val response = deleteService.undelete(actionPayload(urlService.generatePostUrl(post)))

        assertEquals(ApiResponse.Success.NoContent, response)
        assertFalse(postService.findBySlug(post.slug)!!.deleted)
    }
}
