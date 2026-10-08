package com.camtonas.app.nas

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtStatus
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties

/**
 * SMB / CIFS sender backed by jcifs-ng. Works against:
 *   - Windows shares (SMB 1/2/3)
 *   - Samba (Synology, QNAP, Asustor, Western Digital, etc.)
 *   - macOS file sharing
 *   - Routers with USB storage (OpenWrt, AsusWRT-Merlin, etc.)
 *
 * URL form: smb://user:pass@host:port/share/path/...
 *  - port=0 → default 445
 *  - share is the SMB share name (e.g. "photos")
 *  - path is the subdirectory inside the share
 */
class SMBSender(
    private val host: String,
    private val share: String,
    private val basePath: String,        // remote subdir, e.g. "Photos/2025"
    private val username: String,
    private val password: String,
    private val port: Int = 0
) : NASSender {

    override val name: String = "SMB $host/$share"
    override val protocolName: String = "SMB"

    private val ctx: CIFSContext by lazy {
        val props = Properties()
        props["jcifs.smb.client.minVersion"] = "SMB202"
        props["jcifs.smb.client.maxVersion"] = "SMB311"
        props["jcifs.smb.client.useUnicode"] = "true"
        props["jcifs.smb.client.responseTimeout"] = "30000"
        val cfg = PropertyConfiguration(props)
        BaseContext(cfg).withCredentials { username, _ /* domain */, _ /* host */, password.toCharArray() }
    }

    private fun makeUrl(remotePath: String): String {
        val portPart = if (port > 0) ":$port" else ""
        val p = if (remotePath.startsWith("/")) remotePath.substring(1) else remotePath
        return "smb://$host$portPart/$share/$p"
    }

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // First confirm we can list the share root.
            val root = SmbFile(makeUrl(""), ctx)
            root.list()
            // Then make sure the base path exists (create if needed).
            val base = SmbFile(makeUrl(basePath), ctx)
            if (!base.exists()) base.mkdirs()
            Unit
        }
    }

    override suspend fun upload(
        remotePath: String,
        source: () -> InputStream,
        sizeHint: Long,
        onProgress: (Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = makeUrl(remotePath)
            val f = SmbFile(url, ctx)
            // Ensure parent directory exists.
            f.parent?.let { p ->
                if (p.isNotEmpty()) {
                    val parent = SmbFile(p.replace("\\", "/").let { if (it.endsWith("/")) it else "$it/" }, ctx)
                    if (!parent.exists()) parent.mkdirs()
                }
            }
            source().use { input ->
                SmbFileOutputStream(f).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        sent += n
                        onProgress(sent)
                    }
                }
            }
            remotePath
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { ctx.close() }
    }
}
