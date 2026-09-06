package dev.jacobandersen.bastion.graphql

import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.service.PostService
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.url.UrlService
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PostQueryServiceTest {

    private val postService = mock(PostService::class.java)
    private val urlService = mock(UrlService::class.java)
    private val service = PostQueryService(postService, urlService)

    @Test
    fun `feed defaults to limit 10 offset 0 without types`() {
        `when`(postService.findFeedPosts(null, 10, 0)).thenReturn(emptyList())

        assertEquals(emptyList<Post>(), service.feed(null, null, null))
        verify(postService).findFeedPosts(null, 10, 0)
    }

    @Test
    fun `feed maps requested types to lowercase subtypes`() {
        `when`(postService.findFeedPosts(listOf("note", "photo"), 5, 0)).thenReturn(emptyList())

        service.feed(listOf(PostType.NOTE, PostType.PHOTO), 5, 0)
        verify(postService).findFeedPosts(listOf("note", "photo"), 5, 0)
    }

    @Test
    fun `feed validates limit and offset like micropub`() {
        assertThrows(IllegalArgumentException::class.java) { service.feed(null, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { service.feed(null, 101, 0) }
        assertThrows(IllegalArgumentException::class.java) { service.feed(null, 10, -1) }
        assertThrows(IllegalArgumentException::class.java) { service.feed(null, 10, 5) }
    }

    @Test
    fun `post requires exactly one of slug or url`() {
        assertThrows(IllegalArgumentException::class.java) { service.post(null, null) }
        assertThrows(IllegalArgumentException::class.java) { service.post("a", "https://example.com/x") }
    }

    @Test
    fun `post rejects a url outside this instance`() {
        `when`(urlService.extractPostSlug("https://other.example/2026/01/01/x")).thenReturn(null)
        assertThrows(IllegalArgumentException::class.java) {
            service.post(null, "https://other.example/2026/01/01/x")
        }
    }

    @Test
    fun `post resolves slug from a url`() {
        `when`(urlService.extractPostSlug("https://bastion.test/2026/01/01/hello")).thenReturn("hello")
        `when`(postService.findBySlug("hello")).thenReturn(publicPost("hello"))

        assertEquals("hello", service.post(null, "https://bastion.test/2026/01/01/hello")?.slug)
    }

    @Test
    fun `post returns null for unknown posts`() {
        `when`(postService.findBySlug("missing")).thenReturn(null)
        assertNull(service.post("missing", null))
    }

    @Test
    fun `post hides deleted, draft and private posts`() {
        `when`(postService.findBySlug("deleted")).thenReturn(post("deleted", PostStatus.PUBLISHED, PostVisibility.PUBLIC, deleted = true))
        `when`(postService.findBySlug("draft")).thenReturn(post("draft", PostStatus.DRAFT, PostVisibility.PUBLIC))
        `when`(postService.findBySlug("private")).thenReturn(post("private", PostStatus.PUBLISHED, PostVisibility.PRIVATE))

        assertNull(service.post("deleted", null))
        assertNull(service.post("draft", null))
        assertNull(service.post("private", null))
    }

    @Test
    fun `post allows public and unlisted published posts`() {
        `when`(postService.findBySlug("public")).thenReturn(publicPost("public"))
        `when`(postService.findBySlug("unlisted")).thenReturn(post("unlisted", PostStatus.PUBLISHED, PostVisibility.UNLISTED))

        assertEquals("public", service.post("public", null)?.slug)
        assertEquals("unlisted", service.post("unlisted", null)?.slug)
    }

    private fun publicPost(slug: String): Post =
        post(slug, PostStatus.PUBLISHED, PostVisibility.PUBLIC)

    private fun post(slug: String, status: PostStatus, visibility: PostVisibility, deleted: Boolean = false): Post {
        return Post(
            id = UUID.randomUUID(),
            slug = slug,
            status = status,
            visibility = visibility,
            deleted = deleted,
            type = "h-entry",
            subtype = "note",
            post = Mf2Object(
                type = listOf("h-entry"),
                properties = mutableMapOf("name" to listOf(Mf2Value.String(slug))),
                children = null,
            ),
        )
    }
}
