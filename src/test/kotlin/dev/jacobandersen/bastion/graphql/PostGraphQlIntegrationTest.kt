package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.micropub.data.repository.PostRepository
import dev.jacobandersen.bastion.micropub.service.post.CreateService
import dev.jacobandersen.bastion.micropub.service.post.DeleteService
import dev.jacobandersen.bastion.micropub.type.req.MicropubPayload
import dev.jacobandersen.bastion.micropub.type.resp.ApiResponse
import dev.jacobandersen.bastion.webmention.data.domain.ReceivedWebmentionState
import dev.jacobandersen.bastion.webmention.data.domain.WebmentionInteraction
import dev.jacobandersen.bastion.webmention.data.entity.ReceivedWebmentionEntity
import dev.jacobandersen.bastion.webmention.data.repository.ReceivedWebmentionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.security.test.context.support.WithMockUser
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Instant

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
    ],
)
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
@WithMockUser(authorities = ["CREATE", "UPDATE", "DELETE", "UNDELETE"])
class PostGraphQlIntegrationTest {
    @Autowired
    lateinit var createService: CreateService

    @Autowired
    lateinit var deleteService: DeleteService

    @Autowired
    lateinit var postRepository: PostRepository

    @Autowired
    lateinit var receivedWebmentionRepository: ReceivedWebmentionRepository

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
    private lateinit var deletedNote: String

    @BeforeEach
    fun seed() {
        receivedWebmentionRepository.deleteAll()
        postRepository.deleteAll()

        publicNote = uniqueSlug("gql-public-note")
        publicPhoto = uniqueSlug("gql-public-photo")
        unlistedNote = uniqueSlug("gql-unlisted")
        privateNote = uniqueSlug("gql-private")
        draftNote = uniqueSlug("gql-draft")
        deletedNote = uniqueSlug("gql-deleted")

        createPost(publicNote, visibility = "public")
        createPost(publicPhoto, visibility = "public", extra = """"photo":["https://example.com/pic.jpg"],""")
        createPost(unlistedNote, visibility = "unlisted")
        createPost(privateNote, visibility = "private")
        createPost(draftNote, status = "draft")

        val deletedLocation = createPost(deletedNote, visibility = "public")
        deleteService.delete(MicropubPayload.Json(mapper.createObjectNode().put("url", deletedLocation)))

        val noteId = postRepository.findBySlug(publicNote)!!.id!!
        saveWebmention(
            noteId,
            "https://reply.example/1",
            WebmentionInteraction.REPLY,
            ReceivedWebmentionState.VERIFIED,
            "Reply author"
        )
        saveWebmention(
            noteId,
            "https://rsvp.example/1",
            WebmentionInteraction.RSVP,
            ReceivedWebmentionState.VERIFIED,
            "RSVP author"
        )
        saveWebmention(
            noteId,
            "https://rejected.example/1",
            WebmentionInteraction.REPLY,
            ReceivedWebmentionState.REJECTED,
            "Rejected author",
        )
    }

    @Test
    fun listExcludesUnlistedPrivateAndDraftPosts() {
        val slugs =
            graphQlTester
                .document("""query { posts { slug } }""")
                .execute()
                .path("posts")
                .entityList<Map<*, *>>(mapClass)
                .get()
                .map { it["slug"] }

        assertEquals(setOf(publicNote, publicPhoto), slugs.toSet())
    }

    @Test
    fun listFiltersByPostType() {
        val slugs =
            graphQlTester
                .document("""query { posts(types: [NOTE]) { slug } }""")
                .execute()
                .path("posts")
                .entityList<Map<*, *>>(mapClass)
                .get()
                .map { it["slug"] }

        assertEquals(listOf(publicNote), slugs)
    }

    @Test
    fun directQueryReturnsAnUnlistedPostBySlugAndUrl() {
        graphQlTester
            .document(
                """query { post(slug: "$unlistedNote") { __typename ... on Post { slug } } }""",
            ).execute()
            .path("post.slug")
            .entity(String::class.java)
            .isEqualTo(unlistedNote)
    }

    @Test
    fun directQueryHidesPrivateAndDraftPosts() {
        graphQlTester
            .document("""query { post(slug: "$privateNote") { __typename } }""")
            .execute()
            .path("post")
            .valueIsNull()

        graphQlTester
            .document("""query { post(slug: "$draftNote") { __typename } }""")
            .execute()
            .path("post")
            .valueIsNull()
    }

    @Test
    fun deletedPublicPostDirectQueryReturnsGone() {
        graphQlTester
            .document(
                """query { post(slug: "$deletedNote") { __typename ... on PostGone { slug url published } } }""",
            ).execute()
            .path("post.__typename")
            .entity(String::class.java)
            .isEqualTo("PostGone")

        val gone =
            graphQlTester
                .document(
                    """query { post(slug: "$deletedNote") { __typename ... on PostGone { slug url } } }""",
                ).execute()
                .path("post")
                .entity<Map<*, *>>(mapClass)
                .get()

        assertEquals(deletedNote, gone["slug"])
        assertEquals(true, gone["url"] != null)
    }

    @Test
    fun propertiesReturnsOnlyRequestedNames() {
        graphQlTester
            .document(
                """query { post(slug: "$publicNote") { __typename ... on Post { properties(names: ["name"]) } } }""",
            ).execute()
            .path("post.properties")
            .entity<Map<*, *>>(mapClass)
            .satisfies { props -> assertEquals(setOf("name"), props.keys) }
    }

    @Test
    fun singlePostReturnsVerifiedWebmentionsOnly() {
        val webmentions =
            graphQlTester
                .document(
                    """query { post(slug: "$publicNote") { __typename ... on Post { webmentions { sourceUrl interaction authorName } } } }""",
                ).execute()
                .path("post.webmentions")
                .entityList<Map<*, *>>(mapClass)
                .get()

        assertEquals(2, webmentions.size)
        val interactions = webmentions.map { it["interaction"] }.toSet()
        assertEquals(setOf("REPLY", "RSVP"), interactions)
        assertTrue(webmentions.none { it["sourceUrl"] == "https://rejected.example/1" })
    }

    @Test
    fun webmentionCountsAreReportedPerPost() {
        val posts =
            graphQlTester
                .document(
                    """query { posts { slug webmentionCounts { total reply rsvp like } } }""",
                ).execute()
                .path("posts")
                .entityList<Map<*, *>>(mapClass)
                .get()

        val countsBySlug = posts.associate { it["slug"] to it["webmentionCounts"] as Map<*, *> }

        val noteCounts = countsBySlug.getValue(publicNote)
        assertEquals(2, noteCounts["total"])
        assertEquals(1, noteCounts["reply"])
        assertEquals(1, noteCounts["rsvp"])
        assertEquals(0, noteCounts["like"])

        val photoCounts = countsBySlug.getValue(publicPhoto)
        assertEquals(0, photoCounts["total"])
    }

    private fun saveWebmention(
        postId: java.util.UUID,
        sourceUrl: String,
        interaction: WebmentionInteraction,
        state: ReceivedWebmentionState,
        authorName: String,
    ) {
        val now = Instant.now()
        receivedWebmentionRepository.save(
            ReceivedWebmentionEntity(
                postId = postId,
                sourceUrl = sourceUrl,
                targetUrl = "https://bastion.test/post",
                state = state,
                interaction = interaction,
                authorName = authorName,
                authorUrl = "https://$authorName.example".lowercase().replace(" ", ""),
                authorPhoto = null,
                contentText = "content of $authorName",
                contentHtml = null,
                rawMf2 = null,
                lastError = null,
                firstSeenAt = now,
                verifiedAt = if (state == ReceivedWebmentionState.VERIFIED) now else null,
                updatedAtUtc = now,
            ),
        )
    }

    private fun createPost(
        slug: String,
        visibility: String = "public",
        status: String = "published",
        extra: String = "",
    ): String {
        val root =
            mapper.readTree(
                """
                {"type": ["h-entry"], "properties": {
                  "name": ["$slug"],
                  "content": ["$slug content"],
                  $extra
                  "mp-slug": ["$slug"],
                  "post-status": ["$status"],
                  "visibility": ["$visibility"]
                }}
                """.trimIndent(),
            ) as ObjectNode
        val response = createService.create(MicropubPayload.Json(root), null)
        return (response as ApiResponse.Success.Created).location
    }

    private fun uniqueSlug(prefix: String): String = "$prefix-${System.nanoTime()}"
}
