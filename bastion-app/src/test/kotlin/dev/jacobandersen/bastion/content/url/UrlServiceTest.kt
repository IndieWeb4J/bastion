package dev.jacobandersen.bastion.content.url

import dev.jacobandersen.bastion.content.url.UrlService
import dev.jacobandersen.bastion.content.url.UrlService.BastionContentUrlConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun extractsSlugFromUrlWithDifferentScheme() {
        assertEquals("my-slug", service.extractPostSlug("http://test.jacobandersen.dev/2026/09/02/my-slug"))
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

    @Test
    fun extractsSlugWithDefaultPortMatching() {
        assertEquals("my-slug", service.extractPostSlug("https://test.jacobandersen.dev:443/2026/09/02/my-slug"))
    }

    @Test
    fun extractsSlugWithCaseInsensitiveHost() {
        assertEquals("my-slug", service.extractPostSlug("https://TEST.JACOBANDERSEN.DEV/2026/09/02/my-slug"))
    }

    @Test
    fun returnsNullForNonDefaultPort() {
        assertNull(service.extractPostSlug("https://test.jacobandersen.dev:8443/2026/09/02/my-slug"))
    }

    @Test
    fun validatePathPatternRejectsMissingSlugPlaceholder() {
        val invalid =
            UrlService(
                BastionContentUrlConfig(
                    baseUrl = "https://test.jacobandersen.dev",
                    pathPattern = "{year}/{month}/{day}",
                ),
            )

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            invalid.validatePathPattern()
        }
    }

    @Test
    fun identifiesOwnContentUrls() {
        assertTrue(service.isOwnContentUrl("https://test.jacobandersen.dev/2026/09/02/my-slug"))
        assertTrue(service.isOwnContentUrl("http://test.jacobandersen.dev/2026/09/02/my-slug"))
        assertTrue(service.isOwnContentUrl("https://TEST.JACOBANDERSEN.DEV/2026/09/02/my-slug"))
        assertTrue(service.isOwnContentUrl("https://test.jacobandersen.dev:443/2026/09/02/my-slug"))
    }

    @Test
    fun rejectsForeignUrlsAsOwn() {
        assertFalse(service.isOwnContentUrl("https://example.com/2026/09/02/my-slug"))
        assertFalse(service.isOwnContentUrl("https://evil.test.jacobandersen.dev/2026/09/02/my-slug"))
        assertFalse(service.isOwnContentUrl("https://test.jacobandersen.dev:8443/2026/09/02/my-slug"))
        assertFalse(service.isOwnContentUrl("not a url"))
    }
}
