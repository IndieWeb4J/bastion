package dev.jacobandersen.bastion.indieauth.util

import dev.jacobandersen.bastion.url.UrlNormalizer
import java.net.URI

/**
 * URL validation for the IndieAuth endpoints, reusing [UrlNormalizer] for the
 * shared scheme/host checks.
 */
object Uris {
    /**
     * A `redirect_uri` must be an absolute http(s) URL with a host and no
     * fragment. This is validated on the authorization endpoint before any
     * redirect is issued, since an invalid one cannot be redirected to.
     */
    fun isRedirectUri(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (UrlNormalizer.authority(url) == null) return false
        return uri.fragment == null
    }
}
