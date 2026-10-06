package dev.jacobandersen.bastion.api

import dev.jacobandersen.bastion.api.post.PostQueryService
import dev.jacobandersen.bastion.api.post.PublicPostController
import dev.jacobandersen.bastion.api.post.dto.FeedResponse
import dev.jacobandersen.bastion.content.PostMf2Type
import dev.jacobandersen.bastion.content.PostTagFilter
import dev.jacobandersen.bastion.content.PostTypesConfig
import dev.jacobandersen.bastion.content.PostTypesRegistry
import dev.jacobandersen.bastion.content.projection.SyndicationProjectionService
import dev.jacobandersen.bastion.content.projection.WebmentionProjectionService
import dev.jacobandersen.bastion.content.url.UrlService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PublicPostControllerCsvTest {
    private val registry =
        PostTypesRegistry(
            PostTypesConfig(
                postTypes =
                    listOf(
                        PostTypesConfig.PostTypeDefinition(type = "note"),
                        PostTypesConfig.PostTypeDefinition(type = "article"),
                        PostTypesConfig.PostTypeDefinition(type = "reply"),
                        PostTypesConfig.PostTypeDefinition(type = "bookmark"),
                        PostTypesConfig.PostTypeDefinition(type = "mood"),
                    ),
            ),
        )

    private fun controllerWith(queryService: PostQueryService): PublicPostController {
        val urlService: UrlService = mock()
        val webmentionProjection: WebmentionProjectionService = mock()
        whenever(webmentionProjection.byPosts(any())).thenReturn(emptyList())
        return PublicPostController(queryService, urlService, webmentionProjection, mock<SyndicationProjectionService>(), registry)
    }

    private fun mockFeed(): PostQueryService {
        val queryService: PostQueryService = mock()
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
        ).thenReturn(emptyList())
        return queryService
    }

    @Test
    fun `csv splits for h and type`() {
        val queryService = mockFeed()
        val controller = controllerWith(queryService)

        controller.feed(
            listOf("entry,h-card"),
            listOf("note,article"),
            tag = null,
            limit = null,
            offset = null,
            year = null,
            month = null,
            day = null,
        )

        val typeCaptor = argumentCaptor<List<PostMf2Type>?>()
        val typeCaptor2 = argumentCaptor<List<String>?>()

        verify(queryService).feed(
            typeCaptor.capture(),
            typeCaptor2.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )

        assertEquals(listOf("note", "article"), typeCaptor2.firstValue)
        assertEquals(listOf(PostMf2Type.H_ENTRY, PostMf2Type.H_CARD), typeCaptor.firstValue)
    }

    @Test
    fun `repeated and csv combined`() {
        val queryService = mockFeed()
        val controller = controllerWith(queryService)

        controller.feed(
            listOf("entry", "h-card"),
            listOf("note,article", "reply"),
            tag = null,
            limit = null,
            offset = null,
            year = null,
            month = null,
            day = null,
        )

        val typeCaptor = argumentCaptor<List<PostMf2Type>?>()
        val typeCaptor2 = argumentCaptor<List<String>?>()

        verify(queryService).feed(
            typeCaptor.capture(),
            typeCaptor2.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )

        assertEquals(listOf("note", "article", "reply"), typeCaptor2.firstValue)
        assertEquals(listOf(PostMf2Type.H_ENTRY, PostMf2Type.H_CARD), typeCaptor.firstValue)
    }

    @Test
    fun `empty after split yields no filter`() {
        val queryService = mockFeed()
        val controller = controllerWith(queryService)

        // blank values should be ignored -> null
        val result: FeedResponse =
            controller.feed(
                listOf(" , "),
                listOf(""),
                tag = null,
                limit = null,
                offset = null,
                year = null,
                month = null,
                day = null,
            )
        assertEquals(0, result.items.size)
    }

    @Test
    fun `none is parsed as an untagged filter and can be combined with tags`() {
        val queryService = mockFeed()
        val controller = controllerWith(queryService)
        controller.feed(
            listOf("entry"),
            listOf("note"),
            listOf(" NONE, Kotlin ", "JAVA", "kotlin"),
            limit = null,
            offset = null,
            year = null,
            month = null,
            day = null,
        )

        val tagFilterCaptor = argumentCaptor<PostTagFilter?>()
        verify(queryService).feed(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            tagFilterCaptor.capture(),
        )

        assertEquals(
            PostTagFilter(tags = setOf("kotlin", "java"), includeUntagged = true),
            tagFilterCaptor.firstValue,
        )
    }

    @Test
    fun `unknown type is rejected`() {
        val queryService = mockFeed()
        val controller = controllerWith(queryService)

        org.junit.jupiter.api.assertThrows<RuntimeException> {
            controller.feed(
                null,
                listOf("unknown-thing"),
                tag = null,
                limit = null,
                offset = null,
                year = null,
                month = null,
                day = null,
            )
        }
    }
}
