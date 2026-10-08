package com.camtonas.app.camera

import com.camtonas.app.sync.CameraObject
import com.camtonas.app.sync.SyncEvent
import kotlinx.coroutines.flow.Flow

/**
 * One way to talk to a camera. Implementations:
 *   - PTPIPSource  : WiFi, PTP over TCP/15740 (Nikon, Canon, Sony, etc.)
 *   - MTPSource    : USB, Android MediaStore MTP (system-mediated)
 */
interface CameraSource {
    val name: String

    /** A live stream of progress / status / log events. */
    val events: Flow<SyncEvent>

    /** Open a connection / session and confirm we can talk to the camera. */
    suspend fun open(): Result<Unit>

    /** Close gracefully. */
    suspend fun close()

    /** List all media objects in the camera's default storage. */
    suspend fun listObjects(): Result<List<CameraObject>>

    /**
     * Download a single object fully to [destPath]. Returns the file size
     * actually written (bytes). Caller is responsible for deleting [destPath]
     * on failure.
     */
    suspend fun downloadObject(handle: Long, destPath: String): Result<Long>

    /** Optional: delete an object (only when "delete-after-upload" is on). */
    suspend fun deleteObject(handle: Long): Result<Unit>
}
