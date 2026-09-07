package dev.jacobandersen.bastion.webmention.salmention

import dev.jacobandersen.bastion.TestcontainersConfiguration
import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.repository.ReceivedWebmentionRepository
import dev.jacobandersen.bastion.webmention.http.SourceFetch
import dev.jacobandersen.bastion.webmention.http.WebmentionSourceFetcher
import dev.jacobandersen.bastion.webmention.salmention.data.repository.SalmentionResponseRepository
import dev.jacobandersen.bastion.webmention.salmention.service.SalmentionSender
import dev.jacobandersen.bastion.webmention.service.WebmentionReceiverService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "bastion.webmention.salmention.recheck-cooldown-minutes=0",
    ],
)
class SalmentionIntegrationTest {
    @Autowired
    lateinit var receiverService: WebmentionReceiverService

    @Autowired
    lateinit var postService: PostService

    @Autowired
    lateinit var urlService: UrlService

    @Autowired
    lateinit var receivedWebmentionRepository: ReceivedWebmentionRepository

    @Autowired
    lateinit var salmentionResponseRepository: SalmentionResponseRepository

    @MockitoBean
    lateinit var sourceFetcher: WebmentionSourceFetcher

    @MockitoBean
    lateinit var salmentionSender: SalmentionSender

    private val sourceUrl = "https://source.example/reply"
    private val carolUrl = "https://carol.example/reply"
    private val daveUrl = "https://dave.example/reply"

    private lateinit var postId: UUID
    private lateinit var postUrl: String

    @BeforeEach
    fun seed() {
        salmentionResponseRepository.deleteAll()
        receivedWebmentionRepository.deleteAll()

        val post =
            postService.create(
                slug = "salmention-${System.nanoTime()}",
                status = PostStatus.PUBLISHED,
                visibility = PostVisibility.PUBLIC,
                deleted = false,
                post =
                    Mf2Object(
                        type = listOf("h-entry"),
                        properties = mapOf("content" to listOf(Mf2Value.String("a post"))),
                    ),
            )
        postId = post.id
        postUrl = urlService.generatePostUrl(post)
    }

    private fun sourceHtml(nested: List<String> = emptyList()): String {
        val nestedHtml =
            nested.joinToString("\n") { url ->
                """<div class="h-entry"><a class="u-url" href="$url">reply</a></div>"""
            }
        return """
            <article class="h-entry">
              <a href="$postUrl">the post</a>
              <div class="e-content">Bob's reply</div>
              $nestedHtml
            </article>
            """.trimIndent()
    }

    private fun fetch(body: String): SourceFetch = SourceFetch(200, sourceUrl, "text/html", body)

    @Test
    fun `first acceptance resends but does not ingest nested responses`() {
        `when`(sourceFetcher.fetch(sourceUrl)).thenReturn(fetch(sourceHtml(listOf(carolUrl))))

        receiverService.verify(sourceUrl, postUrl, postId)

        verify(salmentionSender, times(1)).resendToActiveTargets(postUrl)
        assertEquals(0, salmentionResponseRepository.findBySourceUrl(sourceUrl).size)
    }

    @Test
    fun `re-receipt ingests new nested responses and resends again`() {
        `when`(sourceFetcher.fetch(sourceUrl))
            .thenReturn(fetch(sourceHtml()))
            .thenReturn(fetch(sourceHtml(listOf(carolUrl))))

        receiverService.verify(sourceUrl, postUrl, postId)
        receiverService.verify(sourceUrl, postUrl, postId)

        val responses = salmentionResponseRepository.findBySourceUrl(sourceUrl)
        assertEquals(listOf(carolUrl), responses.map { it.responseUrl })
        verify(salmentionSender, times(2)).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt with no new responses is idempotent`() {
        `when`(sourceFetcher.fetch(sourceUrl))
            .thenReturn(fetch(sourceHtml(listOf(carolUrl))))
            .thenReturn(fetch(sourceHtml(listOf(carolUrl))))

        receiverService.verify(sourceUrl, postUrl, postId)
        receiverService.verify(sourceUrl, postUrl, postId)
        receiverService.verify(sourceUrl, postUrl, postId)

        val responses = salmentionResponseRepository.findBySourceUrl(sourceUrl)
        assertEquals(listOf(carolUrl), responses.map { it.responseUrl })
        verify(salmentionSender, times(2)).resendToActiveTargets(postUrl)
    }

    @Test
    fun `re-receipt stores each new nested response once`() {
        `when`(sourceFetcher.fetch(sourceUrl))
            .thenReturn(fetch(sourceHtml()))
            .thenReturn(fetch(sourceHtml(listOf(carolUrl))))
            .thenReturn(fetch(sourceHtml(listOf(carolUrl, daveUrl))))

        receiverService.verify(sourceUrl, postUrl, postId)
        receiverService.verify(sourceUrl, postUrl, postId)
        receiverService.verify(sourceUrl, postUrl, postId)

        val responses = salmentionResponseRepository.findBySourceUrl(sourceUrl)
        assertEquals(setOf(carolUrl, daveUrl), responses.map { it.responseUrl }.toSet())
    }
}
