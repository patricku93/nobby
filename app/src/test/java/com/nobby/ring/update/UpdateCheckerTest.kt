package com.nobby.ring.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {
    private class FakeFetcher(private val reply: () -> HttpResponse) : ReleaseFetcher {
        var calls = 0
        var lastUrl: String? = null
        var lastHeaders: Map<String, String> = emptyMap()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls++
            lastUrl = url
            lastHeaders = headers
            return reply()
        }
    }

    private fun checker(fetcher: ReleaseFetcher, current: String = "2.0.0") = UpdateChecker(fetcher, current, Dispatchers.Unconfined)
    private fun ok(body: String) = FakeFetcher { HttpResponse(200, body) }

    private val page = "https://github.com/patricku93/nobby/releases/tag/v2.1.0"
    private val apk = "https://github.com/patricku93/nobby/releases/download/v2.1.0/NoBby-2.1.0.apk"

    private fun release(
        tag: String = "v2.1.0",
        html: String = page,
        assets: String = """[
            {"name":"checksums.txt","browser_download_url":"https://github.com/patricku93/nobby/releases/download/v2.1.0/checksums.txt"},
            {"name":"NoBby-2.1.0.apk","size":9123456,"browser_download_url":"$apk"},
            {"name":"Other.apk","browser_download_url":"https://github.com/patricku93/nobby/releases/download/v2.1.0/Other.apk"}
        ]""",
        extra: String = """"draft": false, "prerelease": false""",
    ) = """{"url":"https://api.github.com/x","id":1,"tag_name":"$tag","name":"NoBby $tag",$extra,"html_url":"$html","body":"Notes","assets":$assets}"""

    @Test
    fun `newer release with an apk asset is available with the first apk`() = runTest {
        assertEquals(UpdateResult.Available("2.1.0", page, apk), checker(ok(release())).check())
    }

    @Test
    fun `newer release without an apk asset has no apk url`() = runTest {
        val onlyText = release(assets = """[{"name":"checksums.txt","browser_download_url":"https://github.com/a/b.txt"}]""")
        assertEquals(UpdateResult.Available("2.1.0", page, null), checker(ok(onlyText)).check())
        assertEquals(UpdateResult.Available("2.1.0", page, null), checker(ok(release(assets = "[]"))).check())
    }

    @Test
    fun `missing assets key is fine`() = runTest {
        val body = """{"tag_name":"v2.1.0","html_url":"$page"}"""
        assertEquals(UpdateResult.Available("2.1.0", page, null), checker(ok(body)).check())
    }

    @Test
    fun `same or older release is up to date`() = runTest {
        assertEquals(UpdateResult.UpToDate, checker(ok(release(tag = "v2.0.0")), current = "2.0.0").check())
        assertEquals(UpdateResult.UpToDate, checker(ok(release(tag = "v1.9.9")), current = "2.0.0").check())
        assertEquals(UpdateResult.UpToDate, checker(ok(release(tag = "v2.0.0-rc1")), current = "2.0.0").check())
    }

    @Test
    fun `draft and prerelease are ignored defensively`() = runTest {
        assertEquals(UpdateResult.UpToDate, checker(ok(release(extra = """"draft": true, "prerelease": false"""))).check())
        assertEquals(UpdateResult.UpToDate, checker(ok(release(extra = """"draft": false, "prerelease": true"""))).check())
    }

    @Test
    fun `malformed or incomplete json fails`() = runTest {
        val bodies = listOf("", "not json", "{", "[]", """{"tag_name":"v2.1.0"}""", """{"html_url":"https://github.com/x"}""", """{"tag_name":"nightly","html_url":"https://github.com/x"}""")
        bodies.forEach { assertEquals("body: $it", UpdateResult.Failed, checker(ok(it)).check()) }
    }

    @Test
    fun `http errors and network errors fail without throwing`() = runTest {
        listOf(404, 403, 500).forEach { code ->
            assertEquals(code.toString(), UpdateResult.Failed, checker(FakeFetcher { HttpResponse(code, release()) }).check())
        }
        assertEquals(UpdateResult.Failed, checker(FakeFetcher { throw IOException("offline") }).check())
        assertEquals(UpdateResult.Failed, checker(FakeFetcher { throw IllegalStateException("boom") }).check())
    }

    @Test
    fun `non-https links are not trusted`() = runTest {
        assertEquals(UpdateResult.Failed, checker(ok(release(html = "http://github.com/x"))).check())
        assertEquals(UpdateResult.Failed, checker(ok(release(html = "javascript:alert(1)"))).check())
        val badApk = release(assets = """[{"name":"a.apk","browser_download_url":"http://evil.example/a.apk"}]""")
        assertEquals(UpdateResult.Available("2.1.0", page, null), checker(ok(badApk)).check())
    }

    @Test
    fun `requests the latest release endpoint with the github accept header`() = runTest {
        val f = ok(release())
        checker(f).check()
        assertEquals("https://api.github.com/repos/patricku93/nobby/releases/latest", f.lastUrl)
        assertEquals("application/vnd.github+json", f.lastHeaders["Accept"])
        assertEquals(1, f.calls)
    }

    @Test
    fun `unparseable current version fails instead of nagging`() = runTest {
        assertEquals(UpdateResult.Failed, checker(ok(release()), current = "weird").check())
    }
}
