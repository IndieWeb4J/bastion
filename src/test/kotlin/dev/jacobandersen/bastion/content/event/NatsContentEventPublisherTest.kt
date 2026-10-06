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

/**
 * Verifies the NATS JetStream publisher against a real broker: the stream is
 * created on demand and a published content event is delivered to a consumer.
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
    fun `creates the stream and delivers a published content event`() {
        val url = "nats://${nats.host}:${nats.getMappedPort(4222)}"
        Nats.connect(Options.builder().server(url).build()).use { connection ->
            val publisher = NatsContentEventPublisher(connection, "CONTENT", "content.>")

            val stream = connection.jetStreamManagement().getStreamInfo("CONTENT")
            assertEquals("CONTENT", stream.configuration.name)

            val subscription = connection.jetStream().subscribe("content.>")
            publisher.publish("content.post.created", "post-1", """{"id":"post-1","version":1}""")

            val message = subscription.nextMessage(Duration.ofSeconds(10))
            assertNotNull(message)
            assertEquals("content.post.created", message.subject)
            assertEquals("""{"id":"post-1","version":1}""", String(message.data))
        }
    }
}
