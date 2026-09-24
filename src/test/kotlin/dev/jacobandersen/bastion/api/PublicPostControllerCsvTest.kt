package dev.jacobandersen.bastion.api

import dev.jacobandersen.bastion.api.dto.FeedResponse
import dev.jacobandersen.bastion.micropub.type.PostMf2Type
import dev.jacobandersen.bastion.micropub.type.PostTertiaryTypeFilter
import dev.jacobandersen.bastion.micropub.type.PostType
import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.webmention.data.service.ReceivedWebmentionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PublicPostControllerCsvTest {
    @Test
    fun `csv splits for subtype and type and tertiaryType`() {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionService: ReceivedWebmentionService = mock()
        whenever(webmentionService.verifiedByPostIds(any())).thenReturn(emptyList())
        whenever(
            queryService.feed(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(emptyList())

        val controller = PublicPostController(queryService, urlService, webmentionService)

        controller.feed(
            listOf("h-entry,h-card"),
            listOf("note,article"),
            listOf("bookmark,none"),
            limit = null,
            offset = null,
            year = null,
            month = null,
            day = null,
        )

        val typeCaptor = argumentCaptor<List<PostMf2Type>?>()
        val subtypeCaptor = argumentCaptor<List<PostType>?>()
        val tertiaryCaptor = argumentCaptor<List<PostTertiaryTypeFilter>?>()

        verify(queryService).feed(
            typeCaptor.capture(),
            subtypeCaptor.capture(),
            tertiaryCaptor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )

        assertEquals(listOf(PostType.NOTE, PostType.ARTICLE), subtypeCaptor.firstValue)
        assertEquals(listOf(PostMf2Type.H_ENTRY, PostMf2Type.H_CARD), typeCaptor.firstValue)
        assertEquals(listOf(PostTertiaryTypeFilter.BOOKMARK, PostTertiaryTypeFilter.NONE), tertiaryCaptor.firstValue)
    }

    @Test
    fun `repeated and csv combined`() {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionService: ReceivedWebmentionService = mock()
        whenever(webmentionService.verifiedByPostIds(any())).thenReturn(emptyList())
        whenever(
            queryService.feed(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(emptyList())

        val controller = PublicPostController(queryService, urlService, webmentionService)

        controller.feed(
            listOf("h-entry", "h-card"),
            listOf("note,article", "reply"),
            listOf("mood,none", "bookmark"),
            limit = null,
            offset = null,
            year = null,
            month = null,
            day = null,
        )

        val typeCaptor = argumentCaptor<List<PostMf2Type>?>()
        val subtypeCaptor = argumentCaptor<List<PostType>?>()
        val tertiaryCaptor = argumentCaptor<List<PostTertiaryTypeFilter>?>()

        verify(queryService).feed(
            typeCaptor.capture(),
            subtypeCaptor.capture(),
            tertiaryCaptor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
        )

        assertEquals(listOf(PostType.NOTE, PostType.ARTICLE, PostType.REPLY), subtypeCaptor.firstValue)
        assertEquals(listOf(PostMf2Type.H_ENTRY, PostMf2Type.H_CARD), typeCaptor.firstValue)
        assertEquals(
            listOf(PostTertiaryTypeFilter.MOOD, PostTertiaryTypeFilter.NONE, PostTertiaryTypeFilter.BOOKMARK),
            tertiaryCaptor.firstValue,
        )
    }

    @Test
    fun `empty after split yields no filter`() {
        val queryService: PostQueryService = mock()
        val urlService: UrlService = mock()
        val webmentionService: ReceivedWebmentionService = mock()
        whenever(webmentionService.verifiedByPostIds(any())).thenReturn(emptyList())
        whenever(
            queryService.feed(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(emptyList())

        val controller = PublicPostController(queryService, urlService, webmentionService)

        // blank values should be ignored -> null
        val result: FeedResponse =
            controller.feed(
                listOf(" , "),
                listOf(""),
                listOf("  , "),
                limit = null,
                offset = null,
                year = null,
                month = null,
                day = null,
            )
        assertEquals(0, result.items.size)
    }
}
