package dev.jacobandersen.bastion.micropub.url

import dev.jacobandersen.bastion.url.UrlService
import dev.jacobandersen.bastion.url.UrlService.BastionContentUrlConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UrlServiceTest {
    private val service =
        UrlService(
            BastionContentUrlConfig(
                baseUrl = "https://test.jacobandersen.dev",
                pathPattern = "{year}/{month}/{day}/{slug}",
            ),
        )

    @Test
    fun extractsSlugFromMatchingUrl() {
        assertEquals("my-slug", service.extractPostSlug("https://test.jacobandersen.dev/2026/09/02/my-slug"))
    }

    @Test
    fun extractsSlugWithTrailingSlash() {
        assertEquals("my-slug", service.extractPostSlug("https://test.jacobandersen.dev/2026/09/02/my-slug/"))
    }

    @Test
    fun extractsSlugFromUrlWithExtraPathSegments() {
        assertEquals("my-slug", service.extractPostSlug("https://test.jacobandersen.dev/2026/09/02/my-slug/extra"))
    }

    @Test
    fun returnsNullForForeignDomain() {
        assertNull(service.extractPostSlug("https://example.com/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForLookalikeSubdomain() {
        assertNull(service.extractPostSlug("https://evil.test.jacobandersen.dev/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForDifferentScheme() {
        assertNull(service.extractPostSlug("http://test.jacobandersen.dev/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForPathWithTooFewParts() {
        assertNull(service.extractPostSlug("https://test.jacobandersen.dev/2026/09"))
    }

    @Test
    fun returnsNullForRelativeUrl() {
        assertNull(service.extractPostSlug("2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForUnparseableUrl() {
        assertNull(service.extractPostSlug("not a url"))
    }
}
