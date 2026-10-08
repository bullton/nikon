package com.camtonas.app.sync

import android.content.Context
import com.camtonas.app.camera.CameraDiscovery
import com.camtonas.app.camera.CameraSource
import com.camtonas.app.camera.MTPSource
import com.camtonas.app.camera.PTPIPSource
import com.camtonas.app.data.AppSettings
import com.camtonas.app.data.ConnectionMode
import com.camtonas.app.data.SettingsRepository
import com.camtonas.app.nas.NASFactory
import com.camtonas.app.nas.NASSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates a single sync run. Listens to settings, opens a
 * [CameraSource], polls for new objects, downloads them, then pushes
 * to a [NASSender]. Notifies progress via the [events] flow.
 *
 * Owns its own coroutine scope so the caller (the foreground service)
 * can cancel it cleanly when the user stops sync.
 */
class SyncEngine(
    private val appContext: Context,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(
        replay = 0, extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    fun start() {
        if (runJob?.isActive == true) return
        runJob = scope.launch {
            _state.value = State.Running
            try {
                runLoop()
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                emit(SyncEvent.Error("engine", e))
            } finally {
                _state.value = State.Idle
            }
        }
    }

    fun stop() {
        runJob?.cancel()
        runJob = null
    }

    private suspend fun runLoop() {
        while (true) {
            val snapshot = settings.flow.first()
            val src = openCamera(snapshot)
            if (src == null) { delay(15_000); continue }

            try {
                val nas = NASFactory.create(snapshot)
                val nasTest = nas.testConnection()
                if (nasTest.isFailure) {
                    emit(SyncEvent.Error("nas", nasTest.exceptionOrNull() ?: RuntimeException("?")))
                    delay(30_000)
                    continue
                }
                runOneCycle(src, nas, snapshot)
            } catch (e: Throwable) {
                emit(SyncEvent.Error("cycle", e))
            } finally {
                try { src.close() } catch (_: Throwable) {}
            }
            delay(snapshot.pollIntervalSec.coerceAtLeast(3) * 1000L)
        }
    }

    private suspend fun openCamera(s: AppSettings): CameraSource? {
        val src: CameraSource = when (s.connectionMode) {
            ConnectionMode.WIFI_PTP_IP -> {
                val host = s.cameraIp.ifBlank {
                    CameraDiscovery.guessCameraIp(appContext) ?: "192.168.1.1"
                }
                PTPIPSource(host, s.cameraPort)
            }
            ConnectionMode.USB_MTP -> {
                val bucket = s.cameraIp.ifBlank { "NIKON Z 30" }
                MTPSource(appContext, bucket)
            }
        }
        return src.open().fold(
            onSuccess = { src },
            onFailure = { e -> emit(SyncEvent.Error("open(${s.connectionMode})", e)); null }
        )
    }

    private suspend fun runOneCycle(src: CameraSource, nas: NASSender, s: AppSettings) {
        val listed = src.listObjects().getOrElse { e ->
            emit(SyncEvent.Error("list", e)); return
        }
        val wanted = listed.filter { obj ->
            when {
                obj.isJpeg  -> s.uploadJpeg
                obj.isRaw   -> s.uploadRaw
                obj.isVideo -> s.uploadVideo
                else -> false
            }
        }
        if (wanted.isEmpty()) {
            emit(SyncEvent.Idle("no new files (${listed.size} on card)"))
            return
        }
        emit(SyncEvent.FoundNew(wanted.size))
        for (obj in wanted) {
            val tmp = File(appContext.cacheDir, "camtonas/${obj.handle}_${obj.name}")
            tmp.parentFile?.mkdirs()
            if (!tmp.exists() || tmp.length() != obj.sizeBytes) {
                emit(SyncEvent.Downloading(obj.name, obj.sizeBytes))
                val t = System.currentTimeMillis()
                val res = src.downloadObject(obj.handle, tmp.absolutePath)
                if (res.isFailure) {
                    tmp.delete()
                    emit(SyncEvent.Error("dl ${obj.name}", res.exceptionOrNull()!!))
                    continue
                }
                emit(SyncEvent.Downloaded(obj.name, res.getOrThrow(), System.currentTimeMillis() - t))
            }
            val remote = remotePathFor(obj, s)
            emit(SyncEvent.Uploading(obj.name, tmp.length()))
            val t = System.currentTimeMillis()
            val up = nas.uploadFile(remote, tmp)
            if (up.isFailure) {
                emit(SyncEvent.Error("up ${obj.name}", up.exceptionOrNull()!!))
                continue
            }
            emit(SyncEvent.Uploaded(obj.name, tmp.length(), System.currentTimeMillis() - t))
            if (s.deleteAfterUpload) {
                val del = src.deleteObject(obj.handle)
                if (del.isFailure) emit(SyncEvent.Skipped(obj.name, "delete failed"))
            }
            tmp.delete()
        }
    }

    private fun remotePathFor(obj: CameraObject, s: AppSettings): String {
        val base = s.nasPath.trim('/').ifEmpty { "Photos" }
        val sub = if (s.organizeByDate && obj.capturedAtMs != null) {
            val fmt = SimpleDateFormat("yyyy/yyyy-MM-dd", Locale.US)
            fmt.format(Date(obj.capturedAtMs))
        } else ""
        return listOf(base, sub, obj.name).filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun emit(e: SyncEvent) { _events.tryEmit(e) }

    sealed interface State {
        data object Idle : State
        data object Running : State
    }
}
