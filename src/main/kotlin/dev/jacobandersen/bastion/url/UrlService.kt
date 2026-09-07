package dev.jacobandersen.bastion.url

import dev.jacobandersen.bastion.micropub.data.domain.Post
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

    fun extractPostSlug(url: String): String? {
        val parsedUrl = runCatching { URI(url) }.getOrNull() ?: return null
        val urlAuthority = UrlNormalizer.authority(url) ?: return null
        val baseAuthority = UrlNormalizer.authority(config.baseUrl) ?: return null

        if (urlAuthority != baseAuthority) {
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

        return urlParts[slugIndex]
    }
}
