package dev.jacobandersen.bastion.webmention.service

import dev.jacobandersen.bastion.url.UrlExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UrlExtractorTest {
    @Test
    fun extractsMultiplePlainUrlsInAppearanceOrder() {
        val links = UrlExtractor.distinctUrls(listOf("See https://example.com/one and http://foo.example/two now"))

        assertEquals(listOf("https://example.com/one", "http://foo.example/two"), links)
    }

    @Test
    fun extractsUrlFromHtmlHref() {
        val links = UrlExtractor.distinctUrls(listOf("""<p>Read <a href="https://example.com/post">this</a></p>"""))

        assertEquals(listOf("https://example.com/post"), links)
    }

    @Test
    fun aggregatesAcrossMultipleTexts() {
        val links = UrlExtractor.distinctUrls(listOf("First https://example.com/a", "Second https://example.com/b"))

        assertEquals(listOf("https://example.com/a", "https://example.com/b"), links)
    }

    @Test
    fun dedupsSchemeVariantsKeepingFirstHttp() {
        val links = UrlExtractor.distinctUrls(listOf("http://example.com/a", "https://example.com/a"))

        assertEquals(listOf("http://example.com/a"), links)
    }

    @Test
    fun dedupsSchemeVariantsKeepingFirstHttps() {
        val links = UrlExtractor.distinctUrls(listOf("https://example.com/a", "http://example.com/a"))

        assertEquals(listOf("https://example.com/a"), links)
    }

    @Test
    fun foldsDefaultPorts() {
        val links = UrlExtractor.distinctUrls(
            listOf("http://example.com:80/a", "https://example.com:443/a", "https://example.com/a")
        )

        assertEquals(listOf("http://example.com:80/a"), links)
    }

    @Test
    fun keepsNonDefaultPortDistinct() {
        val links = UrlExtractor.distinctUrls(listOf("https://example.com:8080/a", "https://example.com/a"))

        assertEquals(listOf("https://example.com:8080/a", "https://example.com/a"), links)
    }

    @Test
    fun ignoresFragments() {
        val links = UrlExtractor.distinctUrls(listOf("https://example.com/a#one", "https://example.com/a#two"))

        assertEquals(listOf("https://example.com/a#one"), links)
    }

    @Test
    fun collapsesTrailingSlash() {
        val links = UrlExtractor.distinctUrls(listOf("https://example.com/a", "https://example.com/a/"))

        assertEquals(listOf("https://example.com/a"), links)
    }

    @Test
    fun treatsQueryAsSignificant() {
        val links = UrlExtractor.distinctUrls(listOf("https://example.com/watch?v=A", "https://example.com/watch?v=B"))

        assertEquals(listOf("https://example.com/watch?v=A", "https://example.com/watch?v=B"), links)
    }

    @Test
    fun stripsTrackingParams() {
        val links = UrlExtractor.distinctUrls(
            listOf("https://example.com/a?utm_source=x&utm_medium=y", "https://example.com/a")
        )

        assertEquals(listOf("https://example.com/a?utm_source=x&utm_medium=y"), links)
    }

    @Test
    fun collapsesQueryParamOrder() {
        val links = UrlExtractor.distinctUrls(listOf("https://example.com/a?b=2&a=1", "https://example.com/a?a=1&b=2"))

        assertEquals(listOf("https://example.com/a?b=2&a=1"), links)
    }

    @Test
    fun ignoresHostCase() {
        val links = UrlExtractor.distinctUrls(listOf("https://EXAMPLE.com/a", "https://example.com/a"))

        assertEquals(listOf("https://EXAMPLE.com/a"), links)
    }

    @Test
    fun excludesNonHttpSchemes() {
        val links = UrlExtractor.distinctUrls(listOf("ftp://example.com/x plus https://example.com/y"))

        assertEquals(listOf("https://example.com/y"), links)
    }

    @Test
    fun returnsEmptyForNoLinks() {
        assertEquals(emptyList<String>(), UrlExtractor.distinctUrls(emptyList()))
        assertEquals(emptyList<String>(), UrlExtractor.distinctUrls(listOf("", "   ")))
    }
}
