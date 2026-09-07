package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.micropub.service.post.CreateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.security.test.context.support.WithMockUser
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.json.JsonMapper

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
    ]
)
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
@WithMockUser(authorities = ["CREATE", "UPDATE", "DELETE", "UNDELETE"])
class PostGraphQlIntegrationTest {

    @Autowired
    lateinit var createService: CreateService

    @Autowired
    lateinit var graphQlTester: GraphQlTester

    private val mapper = JsonMapper.builderWithJackson2Defaults().build()

    @Suppress("UNCHECKED_CAST")
    private val mapClass: Class<Map<*, *>> = Map::class.java as Class<Map<*, *>>

    private lateinit var publicNote: String
    private lateinit var publicPhoto: String
    private lateinit var unlistedNote: String
    private lateinit var privateNote: String
    private lateinit var draftNote: String

    @BeforeEach
    fun seed() {
        publicNote = uniqueSlug("gql-public-note")
        publicPhoto = uniqueSlug("gql-public-photo")
        unlistedNote = uniqueSlug("gql-unlisted")
        privateNote = uniqueSlug("gql-private")
        draftNote = uniqueSlug("gql-draft")

        createPost(publicNote, visibility = "public")
        createPost(publicPhoto, visibility = "public", extra = """"photo":["https://example.com/pic.jpg"],""")
        createPost(unlistedNote, visibility = "unlisted")
        createPost(privateNote, visibility = "private")
        createPost(draftNote, status = "draft")
    }

    @Test
    fun listExcludesUnlistedPrivateAndDraftPosts() {
        val slugs = graphQlTester.document("""query { posts { slug } }""")
            .execute()
            .path("posts")
            .entityList<Map<*, *>>(mapClass)
            .get()
            .map { it["slug"] }

        assertEquals(setOf(publicNote, publicPhoto), slugs.toSet())
    }

    @Test
    fun listFiltersByPostType() {
        val slugs = graphQlTester.document("""query { posts(types: [NOTE]) { slug } }""")
            .execute()
            .path("posts")
            .entityList<Map<*, *>>(mapClass)
            .get()
            .map { it["slug"] }

        assertEquals(listOf(publicNote), slugs)
    }

    @Test
    fun directQueryReturnsAnUnlistedPostBySlugAndUrl() {
        graphQlTester.document("""query { post(slug: "$unlistedNote") { slug } }""")
            .execute()
            .path("post.slug")
            .entity(String::class.java)
            .isEqualTo(unlistedNote)
    }

    @Test
    fun directQueryHidesPrivateAndDraftPosts() {
        graphQlTester.document("""query { post(slug: "$privateNote") { slug } }""")
            .execute()
            .path("post")
            .valueIsNull()

        graphQlTester.document("""query { post(slug: "$draftNote") { slug } }""")
            .execute()
            .path("post")
            .valueIsNull()
    }

    @Test
    fun propertiesReturnsOnlyRequestedNames() {
        graphQlTester.document(
            """query { post(slug: "$publicNote") { properties(names: ["name"]) } }"""
        )
            .execute()
            .path("post.properties")
            .entity<Map<*, *>>(mapClass)
            .satisfies { props -> assertEquals(setOf("name"), props.keys) }
    }

    private fun createPost(slug: String, visibility: String = "public", status: String = "published", extra: String = "") {
        val root = mapper.readTree(
            """
            {"type": ["h-entry"], "properties": {
              "name": ["$slug"],
              "content": ["$slug content"],
              $extra
              "mp-slug": ["$slug"],
              "post-status": ["$status"],
              "visibility": ["$visibility"]
            }}
            """.trimIndent()
        ) as ObjectNode
        val response = createService.create(MicropubPayload.Json(root), null)
        assertEquals(true, response is ApiResponse.Success.Created)
    }

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"
}
