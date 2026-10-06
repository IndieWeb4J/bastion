package dev.jacobandersen.bastion.content.projection

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.beacon.WebmentionInteraction
import dev.jacobandersen.beacon.event.WebmentionEvent
import dev.jacobandersen.beacon.event.WebmentionEventType
import dev.jacobandersen.conduit.event.SyndicationEvent
import dev.jacobandersen.conduit.event.SyndicationEventType
import io.nats.client.Nats
import io.nats.client.Options
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bastion projects Beacon's `webmention.*` and Conduit's `syndication.*` events
 * into its read model. Real Postgres + NATS containers.
 */
@Testcontainers
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "bastion.content.events.nats.enabled=true",
    ],
)
class ProjectionNatsIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val nats: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nats:2.11-alpine"))
                .withCommand("-js")
                .withExposedPorts(4222)

        @JvmStatic
        @DynamicPropertySource
        fun natsProperties(registry: DynamicPropertyRegistry) {
            registry.add("bastion.content.events.nats.url") { "nats://${nats.host}:${nats.getMappedPort(4222)}" }
        }
    }

    private val mapper = jacksonObjectMapper()

    @Autowired
    private lateinit var webmentionProjection: WebmentionProjectionService

    @Autowired
    private lateinit var syndicationProjection: SyndicationProjectionService

    @Test
    fun `projects webmention and syndication events into the read model`() {
        val postId = UUID.randomUUID()
        val webmention =
            WebmentionEvent(
                eventType = WebmentionEventType.VERIFIED,
                postId = postId.toString(),
                sourceUrl = "https://other.example/post",
                targetUrl = "https://example.com/2024/01/01/s",
                interaction = WebmentionInteraction.REPLY,
                authorName = "Someone",
                contentText = listOf("nice"),
            )
        val syndication =
            SyndicationEvent(
                eventType = SyndicationEventType.SYNDICATED,
                postId = postId.toString(),
                targetUid = "bridgy",
                targetName = "Bridgy",
                syndicatedUrl = "https://brid.gy/copy/1",
            )

        Nats.connect(Options.builder().server("nats://${nats.host}:${nats.getMappedPort(4222)}").build()).use { connection ->
            val js = connection.jetStream()
            js.publish("webmention.verified", mapper.writeValueAsBytes(webmention))
            js.publish("syndication.syndicated", mapper.writeValueAsBytes(syndication))
        }

        await { webmentionProjection.byPost(postId).isNotEmpty() }
        val projected = webmentionProjection.byPost(postId).single()
        assertEquals(WebmentionInteraction.REPLY, projected.interaction)
        assertEquals("Someone", projected.authorName)

        await { syndicationProjection.byPost(postId).isNotEmpty() }
        val copied = syndicationProjection.byPost(postId).single()
        assertEquals("bridgy", copied.targetUid)
        assertEquals("Bridgy", copied.name)
        assertEquals("https://brid.gy/copy/1", copied.url)
    }

    private fun await(
        timeoutMillis: Long = 20_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        assertTrue(condition(), "condition not met within ${timeoutMillis}ms")
    }
}
