package com.camtonas.app.nas

import java.io.InputStream
import java.time.Instant

/** A pluggable "upload a file to NAS" backend. */
interface NASSender {
    val name: String
    val protocolName: String

    /** Cheap check that credentials work and the share / root exists. */
    suspend fun testConnection(): Result<Unit>

    /**
     * Upload a stream to [remotePath] (absolute, POSIX-style with '/')
     * and return the path that was actually written. Implementations
     * should create intermediate directories.
     */
    suspend fun upload(
        remotePath: String,
        source: () -> InputStream,
        sizeHint: Long = -1L,
        onProgress: (sent: Long) -> Unit = {}
    ): Result<String>

    /** Upload a local file. Convenience wrapper around [upload]. */
    suspend fun uploadFile(remotePath: String, localFile: java.io.File): Result<String> =
        upload(remotePath, { localFile.inputStream() }, localFile.length())

    suspend fun close() {}
}
