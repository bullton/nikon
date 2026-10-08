package com.camtonas.app.sync

import kotlinx.serialization.Serializable

/**
 * One camera-resident object as seen by the app. Identified by its
 * PTP ObjectHandle (32-bit) which is unique per session. Filename
 * and size are taken from the PTP ObjectInfo dataset.
 */
@Serializable
data class CameraObject(
    val handle: Long,
    val name: String,
    val sizeBytes: Long,
    val mime: String,            // image/jpeg, image/x-nikon-nef, video/mp4, …
    val capturedAtMs: Long? = null
) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val isJpeg get() = ext in setOf("jpg", "jpeg") || mime == "image/jpeg"
    val isRaw   get() = ext in setOf("nef", "nrw", "arw", "cr2", "cr3", "dng", "raf", "orf", "rw2", "pef")
    val isVideo get() = mime.startsWith("video/") || ext in setOf("mp4", "mov", "avi", "mts")
}

sealed interface SyncEvent {
    val ts: Long get() = System.currentTimeMillis()

    data class Idle(val message: String = "") : SyncEvent
    data class Connecting(val target: String) : SyncEvent
    data class Connected(val info: String) : SyncEvent
    data class Disconnected(val reason: String = "") : SyncEvent
    data class FoundNew(val count: Int) : SyncEvent
    data class Downloading(val name: String, val size: Long) : SyncEvent
    data class Downloaded(val name: String, val size: Long, val ms: Long) : SyncEvent
    data class Uploading(val name: String, val size: Long) : SyncEvent
    data class Uploaded(val name: String, val size: Long, val ms: Long) : SyncEvent
    data class Skipped(val name: String, val reason: String) : SyncEvent
    data class Error(val where: String, val cause: Throwable) : SyncEvent {
        constructor(where: String, msg: String) : this(where, RuntimeException(msg))
    }
    data class Log(val level: LogLevel, val tag: String, val message: String) : SyncEvent
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
