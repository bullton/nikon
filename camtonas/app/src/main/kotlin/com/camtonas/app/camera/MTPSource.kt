package com.camtonas.app.camera

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.camtonas.app.sync.CameraObject
import com.camtonas.app.sync.SyncEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * MediaStore-backed CameraSource. When the user connects the camera over
 * USB and the system MTP driver enumerates it, the photos appear in
 * MediaStore under a bucket named after the camera. This source just
 * queries that bucket and copies new entries.
 *
 * Caveats: deletion is not implemented here because MediaStore entries
 * created by the MTP driver are not always deletable without elevated
 * USB permission. For full PTP delete semantics over USB, extend this
 * class with a direct UsbDeviceConnection-based implementation.
 */
class MTPSource(
    private val context: Context,
    private val bucketName: String,
) : CameraSource {

    override val name: String = "USB MTP ($bucketName)"

    private val _events = MutableSharedFlow<SyncEvent>(
        replay = 0, extraBufferCapacity = 32, onBufferOverflow = MutableSharedFlow()
            .let { _ -> kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST }
    )
    override val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    override suspend fun open(): Result<Unit> = runCatching {
        _events.tryEmit(SyncEvent.Connecting(name))
        // Nothing to do — MediaStore is always available.
        _events.tryEmit(SyncEvent.Connected(name))
    }

    override suspend fun close() = runCatching { Unit }

    override suspend fun listObjects(): Result<List<CameraObject>> = withContext(Dispatchers.IO) {
        runCatching {
            val out = ArrayList<CameraObject>()
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val proj = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            val sel = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME}=?"
            val args = arrayOf(bucketName)
            context.contentResolver.query(collection, proj, sel, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    out += CameraObject(
                        handle = id,                                       // id == handle
                        name = c.getString(nameCol),
                        sizeBytes = c.getLong(sizeCol),
                        mime = c.getString(mimeCol) ?: "image/jpeg",
                        capturedAtMs = c.getLong(dateCol).takeIf { it > 0 }
                    )
                }
            }
            out
        }.onFailure { _events.tryEmit(SyncEvent.Error("MediaStore list", it)) }
    }

    override suspend fun downloadObject(handle: Long, destPath: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                    handle
                )
                File(destPath).parentFile?.mkdirs()
                var copied = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destPath).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            copied += n
                        }
                    }
                } ?: error("cannot open uri $uri")
                copied
            }.onFailure { _events.tryEmit(SyncEvent.Error("MediaStore download($handle)", it)) }
        }

    override suspend fun deleteObject(handle: Long): Result<Unit> = withContext(Dispatchers.IO) {
        // Most MTP cameras will not allow random delete through MediaStore
        // without a USB permission grant. Mark unsupported.
        Result.failure(UnsupportedOperationException("delete not supported via MediaStore"))
    }
}
