package dev.jacobandersen.bastion.content.event

import io.nats.client.Nats
import io.nats.client.Options
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Verifies the NATS JetStream publisher against a real broker: the stream is
 * created on demand, a published content event is delivered, a republish under
 * the same message id is deduplicated, and a distinct event for the same post
 * still gets through.
 */
@Testcontainers
class NatsContentEventPublisherTest {
    companion object {
        @Container
        @JvmStatic
        val nats: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nats:2.11-alpine"))
                .withCommand("-js")
                .withExposedPorts(4222)
    }

    @Test
    fun `creates the stream, delivers an event, and dedupes per message id`() {
        val url = "nats://${nats.host}:${nats.getMappedPort(4222)}"
        Nats.connect(Options.builder().server(url).build()).use { connection ->
            val publisher = NatsContentEventPublisher(connection, "CONTENT", "content.>")

            val stream = connection.jetStreamManagement().getStreamInfo("CONTENT")
            assertEquals("CONTENT", stream.configuration.name)

            val subscription = connection.jetStream().subscribe("content.>")
            publisher.publish("content.post.created", "post-1:1", """{"id":"post-1","version":1}""")

            val first = subscription.nextMessage(Duration.ofSeconds(10))
            assertNotNull(first)
            assertEquals("content.post.created", first.subject)
            assertEquals("""{"id":"post-1","version":1}""", String(first.data))
            assertEquals("post-1:1", first.headers?.getFirst("Nats-Msg-Id"))

            // republishing the same event id is dropped by JetStream's dedup window
            publisher.publish("content.post.created", "post-1:1", """{"id":"post-1","version":1}""")
            assertNull(subscription.nextMessage(Duration.ofSeconds(2)))

            // a later event for the same post carries a new id and is delivered
            publisher.publish("content.post.updated", "post-1:2", """{"id":"post-1","version":2}""")
            val second = subscription.nextMessage(Duration.ofSeconds(10))
            assertNotNull(second)
            assertEquals("content.post.updated", second.subject)
        }
    }
}
