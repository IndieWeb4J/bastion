package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.type.mf2.Mf2Value
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.url.UrlService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@WithMockUser(authorities = ["CREATE"])
class CreateServiceTest {
    @Autowired
    lateinit var createService: CreateService

    @Autowired
    lateinit var postService: PostService

    @Autowired
    lateinit var urlService: UrlService

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"

    private fun createPayload(propertiesJson: String): MicropubPayload.Json {
        val root = mapper.readTree("""{"type": ["h-entry"], "properties": $propertiesJson}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    @Test
    fun createsPostWithCommands() {
        val slug = uniqueSlug("commanded")
        val response = createService.create(
            createPayload(
                """
                {
                    "name": ["Hello"],
                    "content": ["World"],
                    "mp-slug": ["$slug"],
                    "post-status": ["draft"],
                    "visibility": ["private"]
                }
                """,
            ),
            null,
        )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val post = postService.findBySlug(slug)!!
        assertEquals(urlService.generatePostUrl(post), (response as ApiResponse.Success.Created).location)
        assertEquals(PostStatus.DRAFT, post.status)
        assertEquals(PostVisibility.PRIVATE, post.visibility)
        assertEquals(emptyList<Mf2Value>(), post.post.getProperty(MicropubCommand.MP_SLUG))
        assertEquals(emptyList<Mf2Value>(), post.post.getProperty(MicropubCommand.POST_STATUS))
        assertEquals(emptyList<Mf2Value>(), post.post.getProperty(MicropubCommand.VISIBILITY))
    }

    @Test
    fun createsPostWithoutCommands() {
        val response = createService.create(
            createPayload(
                """
                {
                    "name": ["Derived name ${System.nanoTime()}"],
                    "content": ["Body"]
                }
                """,
            ),
            null,
        )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val slug = urlService.extractPostSlug((response as ApiResponse.Success.Created).location)!!
        val post = postService.findBySlug(slug)!!
        assertEquals(PostStatus.PUBLISHED, post.status)
        assertEquals(PostVisibility.PUBLIC, post.visibility)
    }

    @Test
    fun rejectsBlankSlugCommand() {
        val response = createService.create(
            createPayload("""{"name": ["Hello"], "mp-slug": [""]}"""),
            null,
        )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun rejectsNonStringSlugCommand() {
        val response = createService.create(
            createPayload("""{"name": ["Hello"], "mp-slug": [42]}"""),
            null,
        )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun rejectsUnknownStatusCommand() {
        val response = createService.create(
            createPayload("""{"name": ["Hello"], "post-status": ["banana"]}"""),
            null,
        )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun rejectsUnknownVisibilityCommand() {
        val response = createService.create(
            createPayload("""{"name": ["Hello"], "visibility": ["banana"]}"""),
            null,
        )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun slugifiesMpSlugAndStripsUnknownCommands() {
        val response = createService.create(
            createPayload(
                """
                {
                    "name": ["Hello"],
                    "content": ["Body"],
                    "mp-slug": ["A space ${System.nanoTime()}"],
                    "mp-syndicate-to": ["https://example.com"]
                }
                """,
            ),
            null,
        )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val location = (response as ApiResponse.Success.Created).location
        val slug = urlService.extractPostSlug(location)!!
        val post = postService.findBySlug(slug)!!
        assertEquals(urlService.generatePostUrl(post), location)
        assertEquals(emptyList<Mf2Value>(), post.post.getProperty("mp-syndicate-to"))
    }

    @Test
    fun preservesHtmlContentObjects() {
        val response = createService.create(
            createPayload("""{"name": ["Hello"], "content": [{"html": "<p>hi</p>"}]}"""),
            null,
        )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val slug = urlService.extractPostSlug((response as ApiResponse.Success.Created).location)!!
        val post = postService.findBySlug(slug)!!
        assertEquals(
            listOf(Mf2Value.Json(mapper.readTree("""{"html": "<p>hi</p>"}"""))),
            post.post.getProperty("content")
        )
    }
}
