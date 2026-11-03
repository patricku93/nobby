package com.nobby.ring.update

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Allow-list for every link the update feature may follow: https, no userinfo, default port, and exactly
 * GitHub (release pages and asset downloads) or its release-asset host. Parsed with [URI], never by string prefix.
 */
object TrustedUrl {
    private val HOSTS = setOf("github.com", "objects.githubusercontent.com")

    /** [url] unchanged when trusted, else null. */
    fun check(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        val trusted = uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            uri.port.let { it == -1 || it == 443 } &&
            uri.host?.lowercase(Locale.ROOT) in HOSTS
        return url.takeIf { trusted }
    }
}
