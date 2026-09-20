package dev.jacobandersen.bastion.micropub.service.post

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.url.UrlService
import org.junit.jupiter.api.Assertions.*
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
@WithMockUser(authorities = ["UPDATE"])
class UpdateServiceTest {
    @Autowired
    lateinit var updateService: UpdateService

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
                        "name" to listOf(Mf2Value.String("Original name")),
                        "content" to listOf(Mf2Value.String("Original content")),
                        "category" to listOf(Mf2Value.String("a"), Mf2Value.String("b")),
                    ),
                children = null,
            )
        return postService.create(slug, PostStatus.PUBLISHED, PostVisibility.PUBLIC, false, obj)
    }

    private fun updateJson(
        url: String,
        update: String,
    ): MicropubPayload.Json {
        val root = mapper.readTree("""{"url": "$url", $update}""") as ObjectNode
        return MicropubPayload.Json(root)
    }

    private fun updateWithUrlOnly(url: String): MicropubPayload.Json =
        MicropubPayload.Json(mapper.createObjectNode().put("url", url))

    @Test
    fun replacesProperty() {
        val post = createPost(uniqueSlug("replace"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"name": ["Updated name"]} """),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        assertEquals(
            listOf(Mf2Value.String("Updated name")),
            postService.findBySlug(post.slug)!!.post.getProperty("name"),
        )
        assertEquals(
            listOf(Mf2Value.String("Original content")),
            postService.findBySlug(post.slug)!!.post.getProperty("content"),
        )
    }

    @Test
    fun addsValues() {
        val post = createPost(uniqueSlug("add"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "add": {"category": ["c"]} """),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        assertEquals(
            listOf(Mf2Value.String("a"), Mf2Value.String("b"), Mf2Value.String("c")),
            postService.findBySlug(post.slug)!!.post.getProperty("category"),
        )
    }

    @Test
    fun deletesProperty() {
        val post = createPost(uniqueSlug("delete-all"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "delete": ["name"] """),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        assertEquals(emptyList<Mf2Value>(), postService.findBySlug(post.slug)!!.post.getProperty("name"))
    }

    @Test
    fun deletesPropertyValues() {
        val post = createPost(uniqueSlug("delete-many"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "delete": {"category": ["b"]} """),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        assertEquals(
            listOf(Mf2Value.String("a")),
            postService.findBySlug(post.slug)!!.post.getProperty("category"),
        )
    }

    @Test
    fun changesSlugAndReturnsCreated() {
        val post = createPost(uniqueSlug("original"))
        val newSlug = uniqueSlug("renamed")

        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"mp-slug": ["$newSlug"]} """),
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        assertEquals(
            urlService.generatePostUrl(postService.findBySlug(newSlug)!!),
            (response as ApiResponse.Success.Created).location
        )
        assertNull(postService.findBySlug(post.slug))
        assertEquals(emptyList<Mf2Value>(), postService.findBySlug(newSlug)!!.post.getProperty("mp-slug"))
    }

    @Test
    fun deduplicatesSlugChange() {
        val first = createPost(uniqueSlug("dedup-first"))
        val second = createPost(uniqueSlug("dedup-second"))
        val expectedSlug = postService.deduplicateSlug(first.slug, second.slug)

        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(second), """ "replace": {"mp-slug": ["${first.slug}"]} """),
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        assertEquals(
            urlService.generatePostUrl(postService.findBySlug(expectedSlug)!!),
            (response as ApiResponse.Success.Created).location
        )
        assertNull(postService.findBySlug(second.slug))
    }

    @Test
    fun appliesStatusAndVisibilityCommands() {
        val post = createPost(uniqueSlug("status"))
        val response =
            updateService.update(
                updateJson(
                    urlService.generatePostUrl(post),
                    """ "replace": {"post-status": ["draft"], "visibility": ["private"]} """,
                ),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        val updated = postService.findBySlug(post.slug)!!
        assertEquals(PostStatus.DRAFT, updated.status)
        assertEquals(PostVisibility.PRIVATE, updated.visibility)
        assertEquals(emptyList<Mf2Value>(), updated.post.getProperty("post-status"))
        assertEquals(emptyList<Mf2Value>(), updated.post.getProperty("visibility"))
    }

    @Test
    fun rejectsBlankSlugCommand() {
        val post = createPost(uniqueSlug("blank-slug"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"mp-slug": [""]} """),
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
        assertEquals(post.slug, postService.findBySlug(post.slug)!!.slug)
    }

    @Test
    fun rejectsNonStringSlugCommand() {
        val post = createPost(uniqueSlug("nonstring-slug"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"mp-slug": [42]} """),
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
        assertEquals(post.slug, postService.findBySlug(post.slug)!!.slug)
    }

    @Test
    fun rejectsUnknownStatusCommand() {
        val post = createPost(uniqueSlug("bad-status"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"post-status": ["banana"]} """),
            )

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
        assertEquals(PostStatus.PUBLISHED, postService.findBySlug(post.slug)!!.status)
    }

    @Test
    fun rejectsUpdateWithoutAnyChanges() {
        val post = createPost(uniqueSlug("noop"))
        val response = updateService.update(updateWithUrlOnly(urlService.generatePostUrl(post)))

        assertInstanceOf(ApiResponse.Error.InvalidRequest::class.java, response)
    }

    @Test
    fun emptyReplaceRemovesProperty() {
        val post = createPost(uniqueSlug("replace-empty"))
        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"category": []} """),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        assertEquals(emptyList<Mf2Value>(), postService.findBySlug(post.slug)!!.post.getProperty("category"))
    }

    @Test
    fun changesSlugWithSpecialCharactersRoundTrips() {
        val post = createPost(uniqueSlug("orig"))
        val requested = "Renamed ${System.nanoTime()}"

        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"mp-slug": ["$requested"]} """),
            )

        assertInstanceOf(ApiResponse.Success.Created::class.java, response)
        val location = (response as ApiResponse.Success.Created).location
        val newSlug = urlService.extractPostSlug(location)!!
        assertNotNull(postService.findBySlug(newSlug))
        assertNull(postService.findBySlug(post.slug))
    }

    @Test
    fun updateBumpsUpdatedButNotPublished() {
        val slug = uniqueSlug("timestamp")
        val obj =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    mutableMapOf(
                        "name" to listOf(Mf2Value.String("Original")),
                        "content" to listOf(Mf2Value.String("Original content")),
                        "published" to listOf(Mf2Value.String("2020-01-01T00:00:00Z")),
                    ),
                children = null,
            )
        val post = postService.create(slug, PostStatus.PUBLISHED, PostVisibility.PUBLIC, false, obj)

        val response =
            updateService.update(
                updateJson(urlService.generatePostUrl(post), """ "replace": {"content": ["Updated content"]} """),
            )

        assertEquals(ApiResponse.Success.NoContent, response)
        val updated = postService.findBySlug(slug)!!
        assertEquals(
            listOf(Mf2Value.String("2020-01-01T00:00:00Z")),
            updated.post.getProperty("published"),
        )
        val updatedProp = (updated.post.getFirstProperty("updated") as Mf2Value.String).value
        assertTrue(OffsetDateTime.parse(updatedProp).year >= 2020)
        assertNotEquals("2020-01-01T00:00:00Z", updatedProp)
    }
}
