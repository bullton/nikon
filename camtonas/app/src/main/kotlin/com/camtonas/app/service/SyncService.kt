package com.camtonas.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.camtonas.app.CamToNasApp
import com.camtonas.app.MainActivity
import com.camtonas.app.R
import com.camtonas.app.sync.SyncEngine
import com.camtonas.app.sync.SyncEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The single foreground service that owns the [SyncEngine]. The UI
 * binds to it via [LocalBinder] only for status updates; the heavy
 * lifting happens regardless of whether the UI is up.
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: SyncEngine
    private var collectJob: Job? = null

    private val _status = MutableSharedFlow<SyncEvent>(
        replay = 8, extraBufferCapacity = 64
    )
    val status: SharedFlow<SyncEvent> = _status.asSharedFlow()

    inner class LocalBinder(val service: SyncService) : android.os.Binder()

    private val binder = LocalBinder(this)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val app = application as CamToNasApp
        engine = SyncEngine(applicationContext, app.settings)
        // Forward engine events to the service's SharedFlow.
        collectJob = scope.launch {
            engine.events.collect { e -> _status.tryEmit(e) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSync()
            ACTION_STOP  -> { stopSync(); stopSelf() }
        }
        return START_STICKY
    }

    private fun startSync() {
        startInForeground()
        engine.start()
    }

    private fun stopSync() {
        engine.stop()
    }

    private fun startInForeground() {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CamToNasApp.CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText("CamToNAS 监听相机中…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        engine.stop()
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 100
        const val ACTION_START = "com.camtonas.app.START"
        const val ACTION_STOP  = "com.camtonas.app.STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, SyncService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, SyncService::class.java).setAction(ACTION_STOP)
            ctx.startService(i)
        }
    }
}
