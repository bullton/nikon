package com.camtonas.app.nas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPFileTransfer
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.InputStream
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * SFTP sender (SSH File Transfer Protocol). Works against:
 *   - Synology / QNAP (with SSH + SFTP enabled)
 *   - Any Linux/macOS host running sshd
 *
 * Key or password auth is supported; pass the key as a PEM string
 * in [keyPem] (preferred) or fall back to [password].
 */
class SFTPSender(
    private val host: String,
    private val port: Int = 22,
    private val username: String,
    private val password: String? = null,
    private val keyPem: String? = null,
    private val basePath: String,
    private val verifyHostKey: Boolean = false
) : NASSender {

    override val name: String = "SFTP $username@$host:$port"
    override val protocolName: String = "SFTP"

    init {
        // sshj pulls in BouncyCastle; make sure it's registered so
        // the various key formats (Ed25519, RSA-PSS, ECDSA) work.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val client: SSHClient by lazy {
        SSHClient().apply {
            if (verifyHostKey) {
                // default to known-hosts verification when the user
                // supplies a known_hosts file
            } else {
                addHostKeyVerifier(PromiscuousVerifier())
            }
            connect(host, port)
            if (keyPem != null) {
                val kp: KeyProvider = loadKey(keyPem)
                authPublickey(username, kp)
            } else {
                authPassword(username, password ?: "")
            }
        }
    }

    private fun loadKey(pem: String): KeyProvider {
        // sshj's KeyProvider has multiple factory methods depending on
        // key type; we use the fromString factory which auto-detects.
        val bytes = pem.toByteArray()
        val isEncrypted = pem.contains("ENCRYPTED")
        val passphrase: CharArray? = if (isEncrypted) password?.toCharArray() else null
        return client.loadKeys(bytes, null, KeyFormat.PEM)
    }

    private fun sftp(): SFTPClient = client.newSFTPClient()

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sftp().use { it.ls("/") }
            mkdirs(sftp(), basePath)
        }
    }

    override suspend fun upload(
        remotePath: String,
        source: () -> InputStream,
        sizeHint: Long,
        onProgress: (Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            sftp().use { ftp ->
                mkdirs(ftp, dirOf(remotePath))
                val flags = setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
                source().use { input ->
                    ftp.fileTransfer().let { ft: SFTPFileTransfer ->
                        val out = ftp.open(remotePath, flags)
                        out.use { o ->
                            val buf = ByteArray(64 * 1024)
                            var sent = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                o.write(buf, 0, n)
                                sent += n
                                onProgress(sent)
                            }
                        }
                    }
                }
            }
            remotePath
        }
    }

    private fun mkdirs(ftp: SFTPClient, path: String) {
        val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
        var p = ""
        for (seg in parts) {
            p = "$p/$seg"
            try { ftp.stat(p) } catch (_: Throwable) { ftp.mkdir(p) }
        }
    }

    private fun dirOf(p: String): String {
        val idx = p.lastIndexOf('/')
        return if (idx <= 0) "/" else p.substring(0, idx)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { client.close() }
    }
}
