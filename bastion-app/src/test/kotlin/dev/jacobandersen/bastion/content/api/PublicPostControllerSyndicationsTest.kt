package dev.jacobandersen.bastion.content.api

import dev.jacobandersen.bastion.content.Post
import dev.jacobandersen.bastion.content.PostStatus
import dev.jacobandersen.bastion.content.PostTypesConfig
import dev.jacobandersen.bastion.content.PostTypesRegistry
import dev.jacobandersen.bastion.content.PostVisibility
import dev.jacobandersen.bastion.content.api.post.PostQueryService
import dev.jacobandersen.bastion.content.api.post.PublicPostController
import dev.jacobandersen.bastion.content.api.post.dto.PostLookupResult
import dev.jacobandersen.bastion.content.api.post.dto.PostResponse
import dev.jacobandersen.bastion.content.projection.ProjectedSyndicationEntity
import dev.jacobandersen.bastion.content.projection.SyndicationProjectionService
import dev.jacobandersen.bastion.content.projection.WebmentionProjectionService
import dev.jacobandersen.bastion.content.url.UrlService
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class PublicPostControllerSyndicationsTest {
    private val postId = UUID.randomUUID()
    private val post =
        Post(
            id = postId,
            slug = "slug",
            status = PostStatus.PUBLISHED,
            visibility = PostVisibility.PUBLIC,
            h = "h-entry",
            post =
                Mf2Object(
                    type = listOf("h-entry"),
                    properties =
                        mapOf(
                            "published" to listOf(Mf2Value.String("2026-01-01T00:00:00Z")),
                            "updated" to listOf(Mf2Value.String("2026-01-02T00:00:00Z")),
                        ),
                ),
        )

    private val registry =
        PostTypesRegistry(PostTypesConfig(postTypes = listOf(PostTypesConfig.PostTypeDefinition(type = "note"))))

    private fun controller(records: List<ProjectedSyndicationEntity>): PublicPostController {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionProjection: WebmentionProjectionService = mock()
        val syndicationProjection: SyndicationProjectionService = mock()
        whenever(queryService.postBySlug("slug")).thenReturn(PostLookupResult.Found(post))
        whenever(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        whenever(webmentionProjection.byPost(postId)).thenReturn(emptyList())
        whenever(syndicationProjection.byPost(postId)).thenReturn(records)
        return PublicPostController(queryService, urlService, webmentionProjection, syndicationProjection, registry)
    }

    private fun body(controller: PublicPostController): PostResponse {
        val response = controller.bySlug("slug")
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body as PostResponse
    }

    @Test
    fun `single lookup includes uid name and url for syndicated copies`() {
        val c =
            controller(
                records =
                    listOf(
                        ProjectedSyndicationEntity(
                            postId = postId,
                            targetUid = "bridgy",
                            name = "Bridgy",
                            url = "https://brid.gy/copy/1",
                            updatedAtUtc = Instant.now(),
                        ),
                    ),
            )
        val syndications = body(c).syndications
        assertEquals(1, syndications?.size)
        assertEquals("bridgy", syndications?.first()?.uid)
        assertEquals("Bridgy", syndications?.first()?.name)
        assertEquals("https://brid.gy/copy/1", syndications?.first()?.url)
    }

    @Test
    fun `feed leaves syndications null`() {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionProjection: WebmentionProjectionService = mock()
        val syndicationProjection: SyndicationProjectionService = mock()
        whenever(
            queryService.feed(
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(listOf(post))
        whenever(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        whenever(webmentionProjection.byPosts(any())).thenReturn(emptyList())
        val controller =
            PublicPostController(queryService, urlService, webmentionProjection, syndicationProjection, registry)
        val feed =
            controller.feed(null, null, null, limit = null, offset = null, year = null, month = null, day = null)
        assertEquals(1, feed.items.size)
        assertNull(feed.items.first().syndications)
    }
}
