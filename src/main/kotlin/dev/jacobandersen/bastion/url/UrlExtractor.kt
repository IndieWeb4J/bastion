package dev.jacobandersen.bastion.url

import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType

internal object UrlExtractor {
    private val linkExtractor =
        LinkExtractor
            .builder()
            .linkTypes(setOf(LinkType.URL))
            .build()

    fun distinctUrls(texts: List<String>): List<String> =
        texts
            .flatMap { text ->
                linkExtractor.extractLinks(text).mapNotNull { span ->
                    val url = text.substring(span.beginIndex, span.endIndex)
                    UrlNormalizer.dedupKey(url)?.let { key -> url to key }
                }
            }.distinctBy { (_, key) -> key }
            .map { (url, _) -> url }
}
