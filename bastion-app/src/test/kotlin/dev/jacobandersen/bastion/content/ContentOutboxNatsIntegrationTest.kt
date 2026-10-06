package dev.jacobandersen.bastion.content

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.content.internal.ContentOutboxDrainer
import dev.jacobandersen.bastion.content.internal.InternalContentService
import dev.jacobandersen.content.client.CreatePostCommand
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
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
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end content event flow: a write through the internal API lands in the
 * Postgres outbox, the drainer publishes it, and a NATS JetStream consumer
 * receives the `content.post.created` event. Real Postgres + NATS containers.
 */
@Testcontainers
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "bastion.content.events.nats.enabled=true",
    ],
)
class ContentOutboxNatsIntegrationTest {
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

    @Autowired
    private lateinit var internalContentService: InternalContentService

    @Autowired
    private lateinit var drainer: ContentOutboxDrainer

    @Test
    fun `create publishes a content event consumed from JetStream`() {
        val natsUrl = "nats://${nats.host}:${nats.getMappedPort(4222)}"
        Nats.connect(Options.builder().server(natsUrl).build()).use { connection ->
            val subscription = connection.jetStream().subscribe("content.>")

            val created =
                internalContentService.create(
                    CreatePostCommand(
                        post =
                            Mf2Object(
                                type = listOf("h-entry"),
                                properties =
                                    mapOf(
                                        "name" to listOf(Mf2Value.String("NATS e2e")),
                                        "content" to listOf(Mf2Value.String("hello from the outbox")),
                                    ),
                            ),
                        slugHint = "nats-e2e-${System.nanoTime()}",
                    ),
                )
            assertEquals(1, created.version)

            drainer.drain()

            val message = subscription.nextMessage(Duration.ofSeconds(15))
            assertNotNull(message, "no content event was published")
            assertEquals("content.post.created", message.subject)
            val payload = String(message.data)
            assertTrue(payload.contains("\"eventType\""), "payload missing eventType: $payload")
            assertTrue(payload.contains("CREATED"), "payload missing CREATED: $payload")
            assertTrue(payload.contains(created.id), "payload missing post id: $payload")
        }
    }
}
