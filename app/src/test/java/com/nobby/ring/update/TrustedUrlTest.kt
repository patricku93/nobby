package com.nobby.ring.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustedUrlTest {
    @Test
    fun `release and download links on github are trusted`() {
        listOf(
            "https://github.com/patricku93/nobby/releases/tag/v2.1.0",
            "https://github.com/patricku93/nobby/releases/download/v2.1.0/NoBby-2.1.0.apk",
            "https://objects.githubusercontent.com/github-production-release-asset/1/a.apk?X-Amz=1",
            "https://github.com:443/p/n",
        ).forEach { assertEquals(it, it, TrustedUrl.check(it)) }
    }

    @Test
    fun `host is compared case-insensitively`() {
        assertEquals("https://GITHUB.com/p/n", TrustedUrl.check("https://GITHUB.com/p/n"))
    }

    @Test
    fun `other hosts, userinfo, schemes and junk are rejected`() {
        listOf(
            "https://github.com@evil.example/",
            "https://github.com:x@evil.example/",
            "https://evil.example/github.com",
            "https://github.com.evil.example/",
            "https://evilgithub.com/",
            "https://evil.example/?u=https://github.com/",
            "https://user@github.com/p/n",
            "https://github.com:8443/p/n",
            "http://github.com/p/n",
            "javascript:alert(1)",
            "file:///sdcard/a.apk",
            "//github.com/p/n",
            "github.com/p/n",
            "https://evil.example\\@github.com/",
            "https://",
            "",
            "   ",
        ).forEach { assertNull(it, TrustedUrl.check(it)) }
        assertNull(TrustedUrl.check(null))
    }
}
