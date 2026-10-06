package dev.jacobandersen.bastion

import dev.jacobandersen.bastion.api.post.PostQueryService
import dev.jacobandersen.bastion.api.tag.TagQueryService
import dev.jacobandersen.bastion.content.PostService
import dev.jacobandersen.bastion.content.PostStatus
import dev.jacobandersen.bastion.content.PostTagFilter
import dev.jacobandersen.bastion.content.PostVisibility
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.dashboard.enabled=false",
        "jobrunr.background-job-server.enabled=false",
    ],
)
@Transactional
class UntaggedPostLookupIntegrationTest {
    @Autowired
    lateinit var postService: PostService

    @Autowired
    lateinit var postQueryService: PostQueryService

    @Autowired
    lateinit var tagQueryService: TagQueryService

    @Test
    fun `none finds only public published untagged posts and combines with ordinary tags`() {
        val prefix = "untagged-lookup-${System.nanoTime()}"
        val publicUntagged = create(prefix + "-public-untagged", PostStatus.PUBLISHED, PostVisibility.PUBLIC, false)
        val publicBlankCategories = create(prefix + "-public-blank", PostStatus.PUBLISHED, PostVisibility.PUBLIC, false, listOf(" ", ""))
        val publicKotlin = create(prefix + "-public-kotlin", PostStatus.PUBLISHED, PostVisibility.PUBLIC, false, listOf("Kotlin", "java"))
        val publicJava = create(prefix + "-public-java", PostStatus.PUBLISHED, PostVisibility.PUBLIC, false, listOf("JAVA"))
        create(prefix + "-unlisted", PostStatus.PUBLISHED, PostVisibility.UNLISTED, false)
        create(prefix + "-private", PostStatus.PUBLISHED, PostVisibility.PRIVATE, false)
        create(prefix + "-draft", PostStatus.DRAFT, PostVisibility.PUBLIC, false)
        create(prefix + "-deleted", PostStatus.PUBLISHED, PostVisibility.PUBLIC, true)

        val untagged =
            postQueryService.feed(
                h = null,
                type = null,
                limitArg = 100,
                offsetArg = 0,
                tagFilter = PostTagFilter.parse(listOf("none")),
            )
        assertEquals(setOf(publicUntagged.slug, publicBlankCategories.slug), untagged.map { it.slug }.toSet())

        val untaggedOrKotlin =
            postQueryService.feed(
                h = null,
                type = null,
                limitArg = 100,
                offsetArg = 0,
                tagFilter = PostTagFilter.parse(listOf("none", "KOTLIN")),
            )
        assertEquals(
            setOf(publicUntagged.slug, publicBlankCategories.slug, publicKotlin.slug),
            untaggedOrKotlin.map { it.slug }.toSet(),
        )

        val kotlinOnly =
            postQueryService.feed(
                h = null,
                type = null,
                limitArg = 100,
                offsetArg = 0,
                tagFilter = PostTagFilter.parse(listOf("kotlin")),
            )
        assertEquals(listOf(publicKotlin.slug), kotlinOnly.map { it.slug })

        val allPublic =
            postQueryService.feed(
                h = null,
                type = null,
                limitArg = 100,
                offsetArg = 0,
            )
        assertTrue(allPublic.map { it.slug }.containsAll(listOf(publicUntagged.slug, publicKotlin.slug, publicJava.slug)))
        assertFalse(allPublic.map { it.slug }.contains(prefix + "-private"))

        val tags = tagQueryService.list(limitArg = 100, offsetArg = 0)
        assertFalse(tags.tags.any { it.tag == PostTagFilter.UNTAGGED })
    }

    private fun create(
        slug: String,
        status: PostStatus,
        visibility: PostVisibility,
        deleted: Boolean,
        categories: List<String> = emptyList(),
    ) = postService.create(
        slug = slug,
        status = status,
        visibility = visibility,
        deleted = deleted,
        post =
            Mf2Object(
                type = listOf("h-entry"),
                properties =
                    buildMap {
                        put("content", listOf(Mf2Value.String("content for $slug")))
                        if (categories.isNotEmpty()) {
                            put("category", categories.map { Mf2Value.String(it) })
                        }
                    },
            ),
    )
}
