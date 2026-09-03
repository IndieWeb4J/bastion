package dev.jacobandersen.bastion.micropub.url

import dev.jacobandersen.bastion.micropub.data.domain.Post
import jakarta.annotation.PostConstruct
import java.net.URI
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service

@Service
class UrlService(val config: BastionContentUrlConfig) {
    @ConfigurationProperties(prefix = "bastion.content")
    data class BastionContentUrlConfig(val baseUrl: String, val pathPattern: String)

    @PostConstruct
    fun validatePathPattern() {
        if (!config.pathPattern.contains("{slug}")) {
            throw IllegalStateException("Content path pattern must contain at least {slug} to generate valid URLs")
        }
    }

    fun generatePostUrl(post: Post): String {
        return "${config.baseUrl}/${generatePostPath(post)}"
    }

    fun generatePostPath(post: Post): String {
        val time = post.createdAt
        return config.pathPattern
            .replace("{year}", time.year.toString())
            .replace("{month}", time.month.value.toString().padStart(2, '0'))
            .replace("{day}", time.dayOfMonth.toString().padStart(2, '0'))
            .replace("{slug}", post.slug)
    }

    fun extractPostSlug(url: String): String? {
        val parsedUrl = runCatching { URI(url) }.getOrNull() ?: return null
        val parsedBaseUrl = runCatching { URI(config.baseUrl) }.getOrNull() ?: return null

        if (parsedUrl.scheme != parsedBaseUrl.scheme ||
            parsedUrl.host != parsedBaseUrl.host ||
            parsedUrl.port != parsedBaseUrl.port
        ) {
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