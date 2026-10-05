package dev.jacobandersen.bastion.api

import dev.jacobandersen.bastion.api.post.PostQueryService
import dev.jacobandersen.bastion.api.post.PublicPostController
import dev.jacobandersen.bastion.api.post.dto.PostLookupResult
import dev.jacobandersen.bastion.api.post.dto.PostResponse
import dev.jacobandersen.bastion.microformats2.Mf2Object
import dev.jacobandersen.bastion.microformats2.Mf2Value
import dev.jacobandersen.bastion.micropub.data.domain.Post
import dev.jacobandersen.bastion.micropub.data.entity.PostSyndicationEntity
import dev.jacobandersen.bastion.micropub.data.service.PostSyndicationService
import dev.jacobandersen.bastion.micropub.syndication.SyndicationConfig
import dev.jacobandersen.bastion.micropub.type.PostStatus
import dev.jacobandersen.bastion.micropub.type.PostVisibility
import dev.jacobandersen.bastion.post.PostTypesConfig
import dev.jacobandersen.bastion.post.PostTypesRegistry
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
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

    private fun controller(
        records: List<PostSyndicationEntity>,
        targets: List<SyndicationConfig.Target>,
    ): PublicPostController {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionService: ReceivedWebmentionService = mock()
        val syndicationService: PostSyndicationService = mock()
        whenever(queryService.postBySlug("slug")).thenReturn(PostLookupResult.Found(post))
        whenever(urlService.generatePostUrl(post)).thenReturn("https://bastion.test/2026/01/01/slug")
        whenever(webmentionService.verifiedByPost(postId)).thenReturn(emptyList())
        whenever(syndicationService.findByPostId(postId)).thenReturn(records)
        return PublicPostController(
            queryService,
            urlService,
            webmentionService,
            syndicationService,
            SyndicationConfig(targets = targets),
            PostTypesRegistry(
                PostTypesConfig(
                    postTypes =
                        listOf(
                            PostTypesConfig.PostTypeDefinition(type = "note"),
                        ),
                ),
            ),
        )
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
                        PostSyndicationEntity(postId = postId, targetUid = "bridgy", syndicatedUrl = "https://brid.gy/copy/1"),
                    ),
                targets =
                    listOf(
                        SyndicationConfig.Target(uid = "bridgy", name = "Bridgy", endpoint = "https://example.test"),
                    ),
            )
        val syndications = body(c).syndications
        assertEquals(1, syndications?.size)
        assertEquals("bridgy", syndications?.first()?.uid)
        assertEquals("Bridgy", syndications?.first()?.name)
        assertEquals("https://brid.gy/copy/1", syndications?.first()?.url)
    }

    @Test
    fun `pending copies and unknown targets are excluded`() {
        val c =
            controller(
                records =
                    listOf(
                        PostSyndicationEntity(postId = postId, targetUid = "bridgy", syndicatedUrl = null),
                        PostSyndicationEntity(postId = postId, targetUid = "stale", syndicatedUrl = "https://stale.test/1"),
                        PostSyndicationEntity(postId = postId, targetUid = "other", syndicatedUrl = "https://other.test/1"),
                    ),
                targets =
                    listOf(
                        SyndicationConfig.Target(uid = "bridgy", name = "Bridgy", endpoint = "https://example.test"),
                        SyndicationConfig.Target(uid = "other", name = "Other", endpoint = "https://other.test"),
                    ),
            )
        val syndications = body(c).syndications
        assertEquals(1, syndications?.size)
        assertEquals("other", syndications?.first()?.uid)
    }

    @Test
    fun `feed leaves syndications null`() {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionService: ReceivedWebmentionService = mock()
        val syndicationService: PostSyndicationService = mock()
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
        whenever(webmentionService.verifiedByPostIds(anyOrNull())).thenReturn(emptyList())
        val controller =
            PublicPostController(
                queryService,
                urlService,
                webmentionService,
                syndicationService,
                SyndicationConfig(),
                PostTypesRegistry(
                    PostTypesConfig(
                        postTypes = listOf(PostTypesConfig.PostTypeDefinition(type = "note")),
                    ),
                ),
            )
        val feed =
            controller.feed(null, null, null, limit = null, offset = null, year = null, month = null, day = null)
        assertEquals(1, feed.items.size)
        assertNull(feed.items.first().syndications)
    }
}
