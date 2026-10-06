package dev.jacobandersen.bastion.content.url

import dev.jacobandersen.bastion.content.Post
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import java.net.URI

@Service
class UrlService(
    val config: BastionContentUrlConfig,
) {
    @ConfigurationProperties(prefix = "bastion.content")
    data class BastionContentUrlConfig(
        val baseUrl: String,
        val pathPattern: String,
    )

    @PostConstruct
    fun validatePathPattern() {
        if (!config.pathPattern.contains("{slug}")) {
            throw IllegalStateException("Content path pattern must contain at least {slug} to generate valid URLs")
        }
    }

    fun generatePostUrl(post: Post): String = "${config.baseUrl.trimEnd('/')}/${generatePostPath(post)}"

    fun generatePostPath(post: Post): String {
        val published =
            post.publishedAt
                ?: throw IllegalStateException("Post '${post.slug}' has no parseable published timestamp")

        return config.pathPattern
            .replace("{year}", published.year.toString())
            .replace("{month}", published.monthValue.toString().padStart(2, '0'))
            .replace("{day}", published.dayOfMonth.toString().padStart(2, '0'))
            .replace("{slug}", post.slug)
    }

    data class ExtractedRef(
        val slug: String,
        val year: Int?,
        val month: Int?,
        val day: Int?,
    )

    fun extractPostRef(url: String): ExtractedRef? {
        val parsedUrl = runCatching { URI(url) }.getOrNull() ?: return null
        val urlAuthority = UrlNormalizer.authority(url) ?: return null
        val baseAuthority = UrlNormalizer.authority(config.baseUrl) ?: return null

        // Lenient on scheme
        if (urlAuthority.host != baseAuthority.host || urlAuthority.port != baseAuthority.port) {
            return null
        }

        val patternParts = config.pathPattern.trim('/').split('/')
        val urlParts = parsedUrl.path.trim('/').split('/')
        if (urlParts.size < patternParts.size) {
            return null
        }

        val slugIndex = patternParts.indexOf("{slug}")
        if (slugIndex < 0) {
            return null
        }

        val slug = urlParts[slugIndex]
        if (slug.isBlank()) return null

        fun parsePart(name: String): Int? {
            val idx = patternParts.indexOf(name)
            if (idx < 0) return null
            return urlParts.getOrNull(idx)?.toIntOrNull()
        }

        return ExtractedRef(
            slug = slug,
            year = parsePart("{year}"),
            month = parsePart("{month}"),
            day = parsePart("{day}"),
        )
    }

    fun extractPostSlug(url: String): String? = extractPostRef(url)?.slug

    /**
     * Whether the URL points at this site's content domain (host + port match
     * against `bastion.content.base-url`, lenient on scheme like
     * [extractPostRef]). Used to suppress self-webmentions.
     */
    fun isOwnContentUrl(url: String): Boolean {
        val urlAuthority = UrlNormalizer.authority(url) ?: return false
        val baseAuthority = UrlNormalizer.authority(config.baseUrl) ?: return false
        return urlAuthority.host == baseAuthority.host && urlAuthority.port == baseAuthority.port
    }
}
