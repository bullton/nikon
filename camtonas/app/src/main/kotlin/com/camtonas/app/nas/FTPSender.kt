package com.camtonas.app.nas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.InputStream

/**
 * FTP / FTPS (explicit TLS) sender. Works against:
 *   - Synology/QNAP with FTP server enabled
 *   - vsftpd, ProFTPD
 *
 * Implicit FTPS (port 990) is supported by passing [useTls] = true
 * with port 990 — the underlying commons-net library uses the same
 * FTPSClient class for both.
 */
class FTPSender(
    private val host: String,
    private val port: Int = 21,
    private val username: String,
    private val password: String,
    private val basePath: String,
    private val useTls: Boolean = false,
    private val passive: Boolean = true
) : NASSender {

    override val name: String = if (useTls) "FTPS $host:$port" else "FTP $host:$port"
    override val protocolName: String = if (useTls) "FTPS" else "FTP"

    private val client: FTPClient by lazy {
        val ftp = if (useTls) FTPSClient() else FTPClient()
        ftp.connect(host, port)
        val reply = ftp.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect()
            error("FTP connect failed: $reply")
        }
        if (!ftp.login(username, password)) {
            ftp.disconnect()
            error("FTP login failed")
        }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        if (useTls && ftp is FTPSClient) {
            ftp.execPBSZ(0)
            ftp.execPROT("P")
        }
        ftp
    }

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Confirm we're logged in
            check(client.isConnected) { "not connected" }
            mkdirs(basePath)
        }
    }

    override suspend fun upload(
        remotePath: String,
        source: () -> InputStream,
        sizeHint: Long,
        onProgress: (Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            mkdirs(dirOf(remotePath))
            source().use { input ->
                // wrap to get progress
                val counting = ProgressInputStream(input) { onProgress(it) }
                val ok = client.storeFile(remotePath, counting)
                if (!ok) error("FTP store failed: ${client.replyString}")
            }
            remotePath
        }
    }

    private fun mkdirs(path: String) {
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        var p = ""
        for (seg in parts) {
            p = "$p/$seg"
            try {
                client.changeWorkingDirectory(p)
            } catch (_: Throwable) {
                client.makeDirectory(p)
                client.changeWorkingDirectory(p)
            }
        }
        // back to root for subsequent uploads
        client.changeWorkingDirectory("/")
    }

    private fun dirOf(p: String): String {
        val idx = p.lastIndexOf('/')
        return if (idx <= 0) "/" else p.substring(0, idx)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching {
            client.logout()
            client.disconnect()
        }
    }

    /** Count the bytes we read from the inner stream and forward to [onProgress]. */
    private class ProgressInputStream(
        private val inner: InputStream,
        private val onProgress: (Long) -> Unit
    ) : InputStream() {
        private var total = 0L
        override fun read(): Int {
            val r = inner.read()
            if (r >= 0) { total++; onProgress(total) }
            return r
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = inner.read(b, off, len)
            if (n > 0) { total += n; onProgress(total) }
            return n
        }
        override fun close() = inner.close()
    }
}
