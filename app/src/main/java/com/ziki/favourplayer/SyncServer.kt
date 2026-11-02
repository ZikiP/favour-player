package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale

class SyncServer(
    private val context: Context,
    private val store: SyncStore,
    private val config: ReceiveConfig,
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    fun start() {
        stop()
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(config.port))
            serverSocket = socket
            SyncBus.publishServer(SyncServerState(running = true, port = config.port))
            Log.i(TAG, "receive server listening on ${config.port}")
            Thread {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        Thread { handle(client) }.apply {
                            isDaemon = true
                            name = "FavourSyncClient"
                            start()
                        }
                    } catch (error: Throwable) {
                        if (!socket.isClosed) Log.w(TAG, "accept failed", error)
                    }
                }
            }.apply {
                isDaemon = true
                name = "FavourSyncAccept"
                start()
            }
        } catch (error: Throwable) {
            SyncBus.publishServer(
                SyncServerState(
                    running = false,
                    port = config.port,
                    error = error.message ?: "端口占用或启动失败",
                ),
            )
            Log.e(TAG, "receive server start failed", error)
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        SyncBus.publishServer(SyncServerState(running = false, port = config.port))
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 15_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val request = HttpRequest.read(input) ?: return

                if (request.method == "OPTIONS") {
                    respond(output, 204, null)
                    return
                }

                when {
                    request.path == "/api/health" && request.method == "GET" -> {
                        val snapshot = store.loadCached()
                        val body = JSONObject()
                            .put("ok", true)
                            .put("updatedAt", snapshot?.updatedAt ?: 0L)
                            .put("count", snapshot?.items?.size ?: 0)
                            .toString()
                        respond(output, 200, body)
                    }

                    request.path == "/api/items" && request.method == "GET" -> {
                        if (!authorized(request)) {
                            respond(output, 401, "{\"error\":\"unauthorized\"}")
                            return
                        }
                        val raw = store.rawSnapshot()
                            ?: "{\"schemaVersion\":1,\"updatedAt\":0,\"items\":[]}"
                        respond(output, 200, raw)
                    }

                    request.path == "/api/items" && request.method == "PUT" -> {
                        if (!authorized(request)) {
                            respond(output, 401, "{\"error\":\"unauthorized\"}")
                            return
                        }
                        val snapshot = try {
                            store.saveSnapshot(request.body)
                        } catch (error: Throwable) {
                            Log.w(TAG, "invalid snapshot", error)
                            respond(output, 400, "{\"error\":\"invalid json\"}")
                            return
                        }
                        SyncBus.publishSnapshot(snapshot)
                        CoverCache.prefetchCached(context)
                        val body = JSONObject()
                            .put("ok", true)
                            .put("count", snapshot.items.size)
                            .put("updatedAt", snapshot.updatedAt)
                            .toString()
                        respond(output, 200, body)
                    }

                    else -> respond(output, 404, "{\"error\":\"not found\"}")
                }
            }.onFailure { Log.w(TAG, "request failed", it) }
        }
    }

    private fun authorized(request: HttpRequest): Boolean =
        store.tokenMatches(request.bearerToken())

    private fun respond(output: BufferedOutputStream, status: Int, body: String?) {
        val reason = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "OK"
        }
        val bytes = body?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type\r\n")
            append("Access-Control-Allow-Methods: GET, PUT, OPTIONS\r\n")
            append("Access-Control-Allow-Private-Network: true\r\n")
            append("Access-Control-Max-Age: 86400\r\n")
            if (status != 204) {
                append("Content-Type: application/json; charset=utf-8\r\n")
            }
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private companion object {
        const val TAG = "FavourPlayer"
    }
}

private class HttpRequest(
    val method: String,
    val path: String,
    private val headers: Map<String, String>,
    val body: String,
) {
    fun bearerToken(): String {
        val value = headers["authorization"].orEmpty()
        return if (value.startsWith("Bearer ", ignoreCase = true)) {
            value.substring(7).trim()
        } else {
            ""
        }
    }

    companion object {
        private const val MAX_HEAD = 16 * 1024
        private const val MAX_BODY = 32 * 1024 * 1024

        fun read(input: InputStream): HttpRequest? {
            val head = readHead(input) ?: return null
            val lines = head.split("\r\n")
            if (lines.isEmpty()) return null
            val parts = lines[0].split(" ")
            if (parts.size < 2) return null

            val method = parts[0].uppercase(Locale.US)
            val path = parts[1].substringBefore('?')
            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val index = line.indexOf(':')
                if (index <= 0) continue
                headers[line.substring(0, index).trim().lowercase(Locale.US)] =
                    line.substring(index + 1).trim()
            }

            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length < 0 || length > MAX_BODY) return null
            val body = if (length > 0) {
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val count = input.read(bytes, read, length - read)
                    if (count < 0) break
                    read += count
                }
                String(bytes, 0, read, StandardCharsets.UTF_8)
            } else {
                ""
            }
            return HttpRequest(method, path, headers, body)
        }

        private fun readHead(input: InputStream): String? {
            val builder = StringBuilder()
            var lastFour = 0
            while (true) {
                val byte = input.read()
                if (byte < 0) return null
                builder.append(byte.toChar())
                lastFour = (lastFour shl 8) or byte
                if (builder.length > MAX_HEAD) return null
                if (lastFour == 0x0D0A0D0A) break
            }
            return builder.toString()
        }
    }
}
