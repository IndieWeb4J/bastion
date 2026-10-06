package dev.jacobandersen.bastion.websub.service

import dev.jacobandersen.bastion.websub.config.WebsubConfig
import dev.jacobandersen.bastion.websub.http.PublishResult
import dev.jacobandersen.bastion.websub.http.WebsubHttpClient
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class WebsubPublisherTest {
    private val jobScheduler = mock(JobScheduler::class.java)
    private val httpClient = mock(WebsubHttpClient::class.java)

    private val topic = "https://bastion.test/feed.xml"
    private val hubs = listOf("https://hub-a.example/", "https://hub-b.example/")

    private fun publisher(
        hubs: List<String>,
        topicUrl: String = topic,
    ): WebsubPublisher =
        WebsubPublisher(
            jobScheduler,
            httpClient,
            WebsubConfig(hubs = hubs, topicUrl = topicUrl),
        )

    @Test
    fun `publish enqueues a job per configured hub`() {
        publisher(hubs).publish()

        verify(jobScheduler, times(2)).enqueue(any())
    }

    @Test
    fun `publish does nothing when no hubs are configured`() {
        publisher(emptyList()).publish()

        verify(jobScheduler, never()).enqueue(any())
    }

    @Test
    fun `publish does nothing when the topic url is blank`() {
        publisher(hubs, topicUrl = "").publish()

        verify(jobScheduler, never()).enqueue(any())
    }

    @Test
    fun `publish ignores blank hub entries`() {
        publisher(listOf("https://hub-a.example/", "   ", "")).publish()

        verify(jobScheduler, times(1)).enqueue(any())
    }

    @Test
    fun `publishToHub posts the configured topic to the hub`() {
        `when`(httpClient.publish("https://hub-a.example/", topic)).thenReturn(PublishResult.Success(204))

        publisher(hubs).publishToHub("https://hub-a.example/")

        verify(httpClient, times(1)).publish("https://hub-a.example/", topic)
    }

    @Test
    fun `publishToHub skips blocked hubs`() {
        publisher(hubs).publishToHub("http://127.0.0.1/hub")

        verify(httpClient, never()).publish(any(), any())
    }

    @Test
    fun `publishToHub throws on retryable failure so JobRunr retries`() {
        `when`(httpClient.publish("https://hub-a.example/", topic))
            .thenReturn(PublishResult.Failure(503, "HTTP 503", retryable = true))

        assertThrows(WebsubPublishException::class.java) {
            publisher(hubs).publishToHub("https://hub-a.example/")
        }
    }

    @Test
    fun `publishToHub does not throw on permanent failure`() {
        `when`(httpClient.publish("https://hub-a.example/", topic))
            .thenReturn(PublishResult.Failure(400, "HTTP 400", retryable = false))

        assertDoesNotThrow { publisher(hubs).publishToHub("https://hub-a.example/") }
    }
}
