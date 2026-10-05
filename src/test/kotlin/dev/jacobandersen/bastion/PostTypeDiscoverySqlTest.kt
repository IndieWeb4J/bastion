package dev.jacobandersen.bastion

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * Verifies that the `post_type_discovery` function in V1 follows the IndieWeb
 * post-type-discovery algorithm and tolerates mf2 value objects (e.g.
 * `{"value": ..., "alt": ...}`) alongside plain URL strings.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostTypeDiscoverySqlTest {
    private val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("postgres:latest"))

    private lateinit var connection: Connection

    @BeforeAll
    fun setUp() {
        postgres.start()
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .load()
            .migrate()
        connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @AfterAll
    fun tearDown() {
        connection.close()
        postgres.stop()
    }

    private fun post(properties: String): String = """{"type":["h-entry"],"properties":{$properties}}"""

    private fun discover(postJson: String): String? {
        connection.prepareStatement("select post_type_discovery(?::jsonb)").use { statement ->
            statement.setString(1, postJson)
            statement.executeQuery().use { result ->
                result.next()
                return result.getString(1)
            }
        }
    }

    private fun expect(
        expected: String,
        properties: String,
    ) {
        assertEquals(expected, discover(post(properties)))
    }

    @Test
    fun `classifies an rsvp by the presence of its value`() {
        expect("rsvp", """"rsvp":["yes"]""")
        expect("rsvp", """"rsvp":[{"value":"yes"}]""")
    }

    @Test
    fun `classifies an in-reply-to url string as a reply`() {
        expect("reply", """"in-reply-to":["https://example.com/a"],"content":["A comment"]""")
    }

    @Test
    fun `classifies an in-reply-to value object as a reply`() {
        expect("reply", """"in-reply-to":[{"value":"https://example.com/a","url":"https://example.com/a"}]""")
    }

    @Test
    fun `a reply with a photo is still a reply`() {
        expect(
            "reply",
            """"in-reply-to":["https://example.com/a"],"photo":["https://example.com/pic.jpg"],"content":["reply"]""",
        )
    }

    @Test
    fun `classifies reposts and likes from url strings`() {
        expect("repost", """"repost-of":["https://example.com/a"]""")
        expect("like", """"like-of":["https://example.com/a"]""")
    }

    @Test
    fun `classifies likes and reposts from value objects`() {
        expect("repost", """"repost-of":[{"value":"https://example.com/a"}]""")
        expect("like", """"like-of":[{"value":"https://example.com/a","alt":"the post"}]""")
    }

    @Test
    fun `classifies video posts from url strings and value objects`() {
        expect("video", """"video":["https://example.com/clip.mp4"]""")
        expect("video", """"video":[{"value":"https://example.com/clip.mp4","alt":"a clip"}]""")
    }

    @Test
    fun `classifies a photo from a url string`() {
        expect("photo", """"photo":["https://example.com/pic.jpg"]""")
    }

    @Test
    fun `classifies a photo from an embedded alt and value object`() {
        expect("photo", """"photo":[{"value":"https://example.com/pic.jpg","alt":"sunset over the bay"}]""")
    }

    @Test
    fun `note and article classification from plain content`() {
        expect("note", """"content":["Hello world"],"name":["Hello"]""")
        expect("article", """"content":["A much longer post body"],"name":["My title"]""")
    }

    @Test
    fun `a post without content is a note`() {
        expect("note", """"name":["Only a name"]""")
        expect("note", """"content":[{"html":"<p>body</p>","value":"body"}]""")
    }

    @Test
    fun `uses summary as content fallback`() {
        expect("note", """"summary":["Hello all"],"name":["Hello"]""")
    }

    @Test
    fun `uses the value of a content html object`() {
        expect("note", """"content":[{"html":"<p>Hello world</p>","value":"Hello world"}],"name":["Hello"]""")
    }

    @Test
    fun `classifies a bookmark by bookmark-of`() {
        expect("bookmark", "\"bookmark-of\":[\"https://example.com/a\"],\"content\":[\"Look at this\"]")
    }

    @Test
    fun `classifies a checkin by checkin property`() {
        expect(
            "checkin",
            "\"checkin\":[{\"type\":[\"h-card\"],\"properties\":{\"name\":[\"Blue Bottle\"]}}]",
        )
    }

    @Test
    fun `classifies a mood by mood property`() {
        expect("mood", "\"mood\":[\"happy\"],\"content\":[\"Feeling good\"]")
    }

    @Test
    fun `a plain note still discovers as a note`() {
        expect("note", "\"content\":[\"Just a thought\"]")
    }

    @Test
    fun `a non h-entry object has no discovered post type`() {
        assertNull(discover("""{"type":["h-card"],"properties":{"name":["Sally"]}}"""))
    }
}
