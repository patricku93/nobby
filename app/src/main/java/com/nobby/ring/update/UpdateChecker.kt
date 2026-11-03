package com.nobby.ring.update

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val version: String, val pageUrl: String, val apkUrl: String?) : UpdateResult
    data object Failed : UpdateResult
}

/** Something that can look for a newer release; [UpdateChecker] is the real one, tests use fakes. */
fun interface ReleaseSource {
    suspend fun check(): UpdateResult
}

/**
 * Asks GitHub for the latest published release and compares its tag with [currentVersion].
 * Never throws: every failure is logged and reported as [UpdateResult.Failed].
 */
class UpdateChecker(
    private val fetcher: ReleaseFetcher,
    private val currentVersion: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReleaseSource {
    override suspend fun check(): UpdateResult = try {
        val response = withContext(dispatcher) { fetcher.get(LATEST_URL, mapOf("Accept" to "application/vnd.github+json")) }
        evaluate(response)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("NoBby", "update check failed", e)
        UpdateResult.Failed
    }

    private fun evaluate(response: HttpResponse): UpdateResult {
        if (response.code !in 200..299) {
            Log.e("NoBby", "update check: HTTP ${response.code}")
            return UpdateResult.Failed
        }
        val current = AppVersion.parse(currentVersion)
        val release = parseRelease(response.body)
        return when {
            current == null || release == null -> UpdateResult.Failed
            release.ignored -> UpdateResult.UpToDate
            release.version > current -> UpdateResult.Available(release.version.toString(), release.pageUrl, release.apkUrl)
            else -> UpdateResult.UpToDate
        }
    }

    private class Release(val version: AppVersion, val pageUrl: String, val apkUrl: String?, val ignored: Boolean)

    private fun parseRelease(body: String): Release? = try {
        val json = JSONObject(body)
        val version = AppVersion.parse(json.optString("tag_name"))
        val page = json.optString("html_url").takeIf(::isHttps)
        if (version == null || page == null) {
            Log.e("NoBby", "update check: release JSON lacks a usable tag or page link")
            null
        } else {
            Release(version, page, firstApk(json), json.optBoolean("draft") || json.optBoolean("prerelease"))
        }
    } catch (e: JSONException) {
        Log.e("NoBby", "update check: malformed JSON", e)
        null
    }

    private fun firstApk(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val url = asset.optString("browser_download_url")
            if (asset.optString("name").endsWith(".apk", ignoreCase = true) && isHttps(url)) return url
        }
        return null
    }

    private fun isHttps(url: String): Boolean = url.startsWith("https://")

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/patricku93/nobby/releases/latest"
    }
}
