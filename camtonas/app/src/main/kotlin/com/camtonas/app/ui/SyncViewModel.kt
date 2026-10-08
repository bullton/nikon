package com.camtonas.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.camtonas.app.CamToNasApp
import com.camtonas.app.data.AppSettings
import com.camtonas.app.data.ConnectionMode
import com.camtonas.app.data.NasProtocol
import com.camtonas.app.service.SyncService
import com.camtonas.app.sync.SyncEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = (app as CamToNasApp).settings
    val settingsFlow: kotlinx.coroutines.flow.Flow<AppSettings> = settings.flow

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastEvent = MutableStateFlow<SyncEvent?>(null)
    val lastEvent: StateFlow<SyncEvent?> = _lastEvent.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    data class Stats(var uploaded: Int = 0, var downloading: Int = 0, var failed: Int = 0)

    private var service: SyncService? = null
    private val bound = MutableStateFlow(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as SyncService.LocalBinder).service
            service = s
            bound.value = true
            viewModelScope.launch {
                s.status.collect { e ->
                    _lastEvent.value = e
                    when (e) {
                        is SyncEvent.Uploaded -> _stats.value = _stats.value.copy(uploaded = _stats.value.uploaded + 1, downloading = 0)
                        is SyncEvent.Uploading -> _stats.value = _stats.value.copy(downloading = 1)
                        is SyncEvent.Error -> _stats.value = _stats.value.copy(failed = _stats.value.failed + 1, downloading = 0)
                        else -> {}
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null; bound.value = false
        }
    }

    fun bindService() {
        val ctx = getApplication<Application>()
        val i = Intent(ctx, SyncService::class.java)
        ctx.bindService(i, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (bound.value) {
            getApplication<Application>().unbindService(connection)
            bound.value = false
        }
    }

    fun toggle() {
        val ctx = getApplication<Application>()
        if (_running.value) {
            SyncService.stop(ctx); _running.value = false
        } else {
            SyncService.start(ctx); _running.value = true
        }
    }

    fun start() { if (!_running.value) { toggle() } }
    fun stop()  { if (_running.value)  { toggle() } }

    fun updateSettings(t: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(t) }
    }
}
