package com.nobby.ring.update

import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

/** The one network call of the app, behind an interface so tests feed a fake JSON body. Blocking; may throw IOException. */
fun interface ReleaseFetcher {
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** Plain [HttpURLConnection]: no extra dependency, no cookies, no user data in the request. */
class HttpUrlConnectionFetcher : ReleaseFetcher {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { readLimited(it) }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(reader: Reader): String {
        val sb = StringBuilder()
        val buf = CharArray(4096)
        while (sb.length < MAX_BODY_CHARS) {
            val n = reader.read(buf)
            if (n < 0) break
            sb.append(buf, 0, n)
        }
        return sb.toString()
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_BODY_CHARS = 1_000_000
    }
}
