package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.MicropubCommand
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.mf24j.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.OffsetDateTime

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
        val response =
            createService.create(
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
        val response =
            createService.create(
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
    fun removesUntaggedCategoryOnCreate() {
        val slug = uniqueSlug("untagged-category")
        val response =
            createService.create(
                createPayload(
                    """{"mp-slug": ["$slug"], "category": [" NoNe ", "kotlin", {"value": "NONE"}]}""",
                ),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        assertEquals(
            listOf(Mf2Value.String("kotlin")),
            postService.findBySlug(slug)!!.post.getProperty("category"),
        )
    }

    @Test
    fun treatsBlankSlugAsAbsent() {
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "mp-slug": [""]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val slug = urlService.extractPostSlug((response as ApiResponse.Success.Created).location)!!
        assertTrue(slug.isNotBlank())
        assertNotNull(postService.findBySlug(slug))

        val whitespaceResponse =
            createService.create(
                createPayload("""{"name": ["Hello"], "mp-slug": ["   "]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, whitespaceResponse)
    }

    @Test
    fun rejectsNonStringSlugCommand() {
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "mp-slug": [42]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun rejectsUnknownStatusCommand() {
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "post-status": ["banana"]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun rejectsUnknownVisibilityCommand() {
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "visibility": ["banana"]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun slugifiesMpSlugAndStripsUnknownCommands() {
        val response =
            createService.create(
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
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "content": [{"html": "<p>hi</p>"}]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val slug = urlService.extractPostSlug((response as ApiResponse.Success.Created).location)!!
        val post = postService.findBySlug(slug)!!
        assertEquals(
            listOf(Mf2Value.Json(mapper.readTree("""{"html": "<p>hi</p>"}"""))),
            post.post.getProperty("content"),
        )
    }

    @Test
    fun acceptsUnlistedVisibility() {
        val slug = uniqueSlug("unlisted")
        val response =
            createService.create(
                createPayload("""{"name": ["Hello"], "mp-slug": ["$slug"], "visibility": ["unlisted"]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        assertEquals(PostVisibility.UNLISTED, postService.findBySlug(slug)!!.visibility)
    }

    @Test
    fun stampsPublishedAndUpdatedOnCreate() {
        val response =
            createService.create(
                createPayload("""{"name": ["Hello ${System.nanoTime()}"]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val slug = urlService.extractPostSlug((response as ApiResponse.Success.Created).location)!!
        val post = postService.findBySlug(slug)!!

        val published = post.post.getFirstProperty("published") as? Mf2Value.String
        val updated = post.post.getFirstProperty("updated") as? Mf2Value.String
        assertNotNull(published)
        assertNotNull(updated)
        assertTrue(OffsetDateTime.parse(published!!.value).year > 2020)
        assertTrue(OffsetDateTime.parse(updated!!.value).year > 2020)
    }

    @Test
    fun preservesAndNormalizesBackdatedPublished() {
        val response =
            createService.create(
                createPayload("""{"name": ["Old"], "published": ["2019-06-01T10:00:00"]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val location = (response as ApiResponse.Success.Created).location
        val slug = urlService.extractPostSlug(location)!!
        val post = postService.findBySlug(slug)!!

        assertTrue(location.contains("/2019/06/01/"), "expected backdated date in location, got $location")
        val published = (post.post.getFirstProperty("published") as Mf2Value.String).value
        assertEquals("2019-06-01T10:00:00+08:00", published)
    }

    @Test
    fun rejectsInvalidPublishedValue() {
        val response =
            createService.create(
                createPayload("""{"name": ["X"], "published": ["not-a-date"]}"""),
                null,
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }
}
