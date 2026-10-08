package com.camtonas.app.nas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * WebDAV sender (RFC 4918) over HTTP/HTTPS. Works against:
 *   - Synology / QNAP WebDAV server
 *   - Nextcloud
 *   - Apache mod_dav
 *   - nginx with WebDAV
 *
 * Uses a single PUT per upload. Intermediate directory creation is
 * done with MKCOL. Authentication is HTTP Basic; for Digest/Negotiate
 * you'd want to swap to a different library.
 */
class WebDAVSender(
    private val host: String,
    private val port: Int = 0,
    private val username: String,
    private val password: String,
    private val basePath: String,
    private val useTls: Boolean = false
) : NASSender {

    override val name: String =
        if (useTls) "WebDAV https://$host:$port" else "WebDAV http://$host:$port"
    override val protocolName: String = "WebDAV"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    private fun baseUrl(): String {
        val p = if (port > 0) ":$port" else ""
        val scheme = if (useTls) "https" else "http"
        return "$scheme://$host$p"
    }

    private fun credHeader(): String = Credentials.basic(username, password)

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // PROPFIND on the base
            val url = "${baseUrl()}/" + (if (basePath.startsWith("/")) basePath else "/$basePath")
            val req = Request.Builder()
                .url(url)
                .method("PROPFIND", "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:allprop/></d:propfind>".toRequestBody("application/xml".toMediaTypeOrNull()))
                .header("Authorization", credHeader())
                .header("Depth", "0")
                .build()
            http.newCall(req).execute().use { r ->
                check(r.isSuccessful || r.code == 207) { "PROPFIND ${r.code} ${r.message}" }
            }
            // Create the base path if it doesn't exist
            ensureCollection(url)
        }
    }

    override suspend fun upload(
        remotePath: String,
        source: () -> InputStream,
        sizeHint: Long,
        onProgress: (Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl()}" + (if (remotePath.startsWith("/")) remotePath else "/$remotePath")
            // Walk up the path and ensure each parent is a collection
            var p = url.substringBeforeLast('/')
            while (p.length > baseUrl().length) {
                ensureCollection(p)
                p = p.substringBeforeLast('/')
            }
            val body = CountingRequestBody(source().readBytes(), onProgress)
            val req = Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", credHeader())
                .header("Content-Type", "application/octet-stream")
                .build()
            http.newCall(req).execute().use { r ->
                check(r.isSuccessful) { "PUT ${r.code} ${r.message}" }
            }
            remotePath
        }
    }

    private fun ensureCollection(url: String) {
        val head = Request.Builder().url(url).head()
            .header("Authorization", credHeader()).build()
        val headResp = http.newCall(head).execute()
        headResp.use { r -> if (r.code == 404) mkcol(url) }
    }

    private fun mkcol(url: String) {
        val body: RequestBody = ByteArray(0).toRequestBody(null, 0, 0)
        val req = Request.Builder().url(url).method("MKCOL", body)
            .header("Authorization", credHeader()).build()
        http.newCall(req).execute().use { r ->
            // 201 Created or 405 Method Not Allowed (already exists) are both fine.
            check(r.isSuccessful || r.code == 405) { "MKCOL ${r.code} ${r.message}" }
        }
    }

    private class CountingRequestBody(
        private val bytes: ByteArray,
        private val onProgress: (Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
        override fun contentLength(): Long = bytes.size.toLong()
        override fun writeTo(sink: okio.BufferedSink) {
            val chunk = 64 * 1024
            var off = 0
            while (off < bytes.size) {
                val end = minOf(off + chunk, bytes.size)
                sink.write(bytes, off, end - off)
                off = end
                onProgress(off.toLong())
            }
        }
    }
}
