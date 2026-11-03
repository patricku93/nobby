package com.nobby.ring.update

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Talks to a tiny loopback HTTP server (plain sockets: the JDK's HttpServer is not visible to the Android test compile). */
class HttpUrlConnectionFetcherTest {
    private lateinit var server: ServerSocket
    private val requests = CopyOnWriteArrayList<List<String>>() // request line + header lines

    private fun url(path: String) = "http://localhost:${server.localPort}$path"

    private fun respond(socket: Socket, status: String, headers: List<String>, body: String) {
        val bytes = body.toByteArray()
        val head = "HTTP/1.1 $status\r\n" + headers.joinToString("") { "$it\r\n" } + "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
    }

    private fun handle(socket: Socket) = socket.use {
        val reader = it.getInputStream().bufferedReader()
        val lines = generateSequence { reader.readLine() }.takeWhile { line -> line.isNotEmpty() }.toList()
        requests += lines
        when (lines.first().split(" ")[1]) {
            "/ok" -> respond(it, "200 OK", emptyList(), "{\"tag_name\":\"v2.1.0\"}")
            "/missing" -> respond(it, "404 Not Found", emptyList(), "{\"message\":\"Not Found\"}")
            "/big" -> respond(it, "200 OK", emptyList(), "x".repeat(50_000))
            "/slow" -> Thread.sleep(1_500)
            "/redirect" -> respond(it, "302 Found", listOf("Location: /ok"), "")
        }
    }

    @Before
    fun setUp() {
        server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(socket) } }
            }
        }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `returns code and body of a successful GET and sends exactly the given headers`() {
        val headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "NoBby/2.0.0")
        assertEquals(HttpResponse(200, "{\"tag_name\":\"v2.1.0\"}"), HttpUrlConnectionFetcher().get(url("/ok"), headers))
        val sent = requests.single()
        assertTrue(sent.first().startsWith("GET /ok "))
        val h = sent.drop(1).associate { line -> line.substringBefore(":").lowercase() to line.substringAfter(":").trim() }
        assertEquals("NoBby/2.0.0", h["user-agent"])
        assertEquals("application/vnd.github+json", h["accept"])
        assertNull(h["cookie"])
        assertNull(h["authorization"])
    }

    @Test
    fun `an error status still returns its body from the error stream`() {
        assertEquals(HttpResponse(404, "{\"message\":\"Not Found\"}"), HttpUrlConnectionFetcher().get(url("/missing"), emptyMap()))
    }

    @Test
    fun `the body is cut at the cap`() {
        val r = HttpUrlConnectionFetcher(maxBodyChars = 8_192).get(url("/big"), emptyMap())
        assertEquals(200, r.code)
        assertTrue("got ${r.body.length}", r.body.length in 8_192 until 50_000)
    }

    @Test(expected = SocketTimeoutException::class)
    fun `a server that stays silent times out`() {
        HttpUrlConnectionFetcher(timeoutMs = 300).get(url("/slow"), emptyMap())
    }

    @Test
    fun `follows redirects`() {
        assertEquals(HttpResponse(200, "{\"tag_name\":\"v2.1.0\"}"), HttpUrlConnectionFetcher().get(url("/redirect"), emptyMap()))
    }
}
