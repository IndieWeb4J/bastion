package dev.jacobandersen.bastion.webmention.controller

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import dev.jacobandersen.bastion.webmention.service.WebmentionReceiverService
import java.util.UUID
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WebmentionControllerTest {

    private lateinit var notificationService: ReceivedWebmentionService
    private lateinit var receiverService: WebmentionReceiverService
    private lateinit var postService: PostService
    private lateinit var urlService: UrlService
    private lateinit var jobScheduler: JobScheduler
    private lateinit var mockMvc: MockMvc

    private val sourceUrl = "https://source.example/reply"
    private val targetUrl = "https://test.jacobandersen.dev/2026/01/01/hello"
    private val postId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        notificationService = mock(ReceivedWebmentionService::class.java)
        receiverService = mock(WebmentionReceiverService::class.java)
        postService = mock(PostService::class.java)
        urlService = mock(UrlService::class.java)
        jobScheduler = mock(JobScheduler::class.java)

        mockMvc = MockMvcBuilders.standaloneSetup(
            WebmentionController(
                notificationService = notificationService,
                receiverService = receiverService,
                postService = postService,
                urlService = urlService,
                jobScheduler = jobScheduler,
            )
        ).build()

        `when`(urlService.extractPostSlug(targetUrl)).thenReturn("hello")
        `when`(postService.findBySlug("hello")).thenReturn(publicPost("hello"))
    }

    @Test
    fun `accepts a valid webmention with 202 and records it as pending`() {
        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", sourceUrl)
                .param("target", targetUrl)
        )
            .andExpect(status().isAccepted)

        verify(notificationService).ensurePending(sourceUrl, targetUrl, postId)
    }

    @Test
    fun `rejects a missing source`() {
        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("target", targetUrl)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))

    }

    @Test
    fun `rejects a malformed source url`() {
        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", "not a url")
                .param("target", targetUrl)
        )
            .andExpect(status().isBadRequest)

    }

    @Test
    fun `rejects a non-http scheme`() {
        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", "mailto:someone@example.com")
                .param("target", targetUrl)
        )
            .andExpect(status().isBadRequest)

    }

    @Test
    fun `rejects a source equal to the target`() {
        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", targetUrl)
                .param("target", targetUrl)
        )
            .andExpect(status().isBadRequest)

    }

    @Test
    fun `rejects a target that is not a post on this site`() {
        val foreign = "https://other.example/2026/01/01/nope"
        `when`(urlService.extractPostSlug(foreign)).thenReturn(null)

        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", sourceUrl)
                .param("target", foreign)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_request"))

    }

    @Test
    fun `rejects a target whose post is not publicly reachable`() {
        `when`(postService.findBySlug("hello")).thenReturn(
            post(slug = "hello", status = PostStatus.DRAFT, visibility = PostVisibility.PUBLIC)
        )

        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", sourceUrl)
                .param("target", targetUrl)
        )
            .andExpect(status().isBadRequest)

    }


    @Test
    fun `rejects a target whose post is deleted`() {
        `when`(postService.findBySlug("hello")).thenReturn(
            post(slug = "hello", status = PostStatus.PUBLISHED, visibility = PostVisibility.PUBLIC, deleted = true)
        )

        mockMvc.perform(
            post("/webmention")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("source", sourceUrl)
                .param("target", targetUrl)
        )
            .andExpect(status().isBadRequest)

    }


    private fun publicPost(slug: String): Post {
        return post(slug, PostStatus.PUBLISHED, PostVisibility.PUBLIC)
    }

    private fun post(slug: String, status: PostStatus, visibility: PostVisibility, deleted: Boolean = false): Post {
        return Post(
            id = postId,
            slug = slug,
            status = status,
            visibility = visibility,
            deleted = deleted,
            type = "h-entry",
            subtype = null,
            post = Mf2Object(
                type = listOf("h-entry"),
                properties = mutableMapOf(
                    "name" to listOf(Mf2Value.String(slug)),
                    "published" to listOf(Mf2Value.String("2026-01-01T00:00:00Z")),
                ),
                children = null,
            ),
        )
    }
}
