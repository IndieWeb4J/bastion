package dev.jacobandersen.bastion.indieauth.service

import dev.jacobandersen.bastion.indieauth.config.IndieAuthConfig
import dev.jacobandersen.bastion.url.UrlNormalizer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.io.IOException

private val logger = KotlinLogging.logger {}

/** The outcome of verifying that an authenticated identity is the site owner. */
sealed interface OwnerVerification {
    /** The configured [me][IndieAuthConfig.me] document links back to the profile. */
    data object Verified : OwnerVerification

    /** The `me` document was fetched but does not `rel=me` link to the profile. */
    data object NotLinked : OwnerVerification

    /** The `me` document could not be fetched, so ownership could not be decided. */
    data object Unavailable : OwnerVerification
}

/**
 * Verifies that an authenticated identity is the site owner: the configured
 * [me][IndieAuthConfig.me] profile document must carry a `rel="me"` link to the
 * identity provider's profile URL. This is the only owner authorization Bastion
 * performs, since it has no local accounts and issues tokens for exactly one
 * identity - `me`.
 */
@Component
class OwnerVerifier(
    private val config: IndieAuthConfig,
) {
    fun verify(profileUrl: String?): OwnerVerification {
        val profile = profileUrl?.takeIf { it.isNotBlank() } ?: return OwnerVerification.NotLinked

        val document =
            try {
                Jsoup
                    .connect(config.me)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .timeout(READ_TIMEOUT_MS)
                    .get()
            } catch (e: IOException) {
                logger.warn(e) { "Failed to fetch ${config.me} for owner verification" }
                return OwnerVerification.Unavailable
            }

        return if (hasRelMeLink(document, profile)) OwnerVerification.Verified else OwnerVerification.NotLinked
    }

    companion object {
        private const val USER_AGENT = "BastionIndieAuth/0.0.1"
        private const val READ_TIMEOUT_MS = 10_000

        internal fun hasRelMeLink(
            document: Document,
            profileUrl: String,
        ): Boolean {
            val expected = UrlNormalizer.identity(profileUrl) ?: return false
            return document.select("a[href][rel~=me], link[href][rel~=me]").any { element ->
                val href = element.absUrl("href")
                href.isNotBlank() && UrlNormalizer.identity(href) == expected
            }
        }
    }
}
