package com.camtonas.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A persistent, observable snapshot of all user-tunable settings. */
data class AppSettings(
    // Connection
    val connectionMode: ConnectionMode = ConnectionMode.WIFI_PTP_IP,
    val cameraIp: String = "192.168.1.1",
    val cameraPort: Int = 15740,
    val pollIntervalSec: Int = 10,

    // File selection
    val uploadJpeg: Boolean = true,
    val uploadRaw: Boolean = false,
    val uploadVideo: Boolean = false,
    val onlyNew: Boolean = true,

    // NAS
    val nasProtocol: NasProtocol = NasProtocol.SMB,
    val nasHost: String = "",
    val nasPort: Int = 0,                 // 0 = protocol default
    val nasShare: String = "",             // SMB share name / WebDAV path
    val nasPath: String = "/Photos",      // remote subdir
    val nasUsername: String = "",
    val nasPassword: String = "",
    val useTls: Boolean = false,           // for WebDAV / FTPS / SFTP

    // Behaviour
    val deleteAfterUpload: Boolean = false,
    val runOnMeteredNetwork: Boolean = false,
    val organizeByDate: Boolean = true
)

enum class ConnectionMode { WIFI_PTP_IP, USB_MTP }
enum class NasProtocol { SMB, SFTP, FTP, WEBDAV }

private val Context.dataStore by preferencesDataStore(name = "camtonas")

class SettingsRepository(private val ctx: Context) {
    private object Keys {
        val CONN_MODE       = stringPreferencesKey("conn_mode")
        val CAMERA_IP       = stringPreferencesKey("camera_ip")
        val CAMERA_PORT     = intPreferencesKey("camera_port")
        val POLL_INTERVAL   = intPreferencesKey("poll_interval")
        val UPLOAD_JPEG     = booleanPreferencesKey("upload_jpeg")
        val UPLOAD_RAW      = booleanPreferencesKey("upload_raw")
        val UPLOAD_VIDEO    = booleanPreferencesKey("upload_video")
        val ONLY_NEW        = booleanPreferencesKey("only_new")
        val NAS_PROTOCOL    = stringPreferencesKey("nas_protocol")
        val NAS_HOST        = stringPreferencesKey("nas_host")
        val NAS_PORT        = intPreferencesKey("nas_port")
        val NAS_SHARE       = stringPreferencesKey("nas_share")
        val NAS_PATH        = stringPreferencesKey("nas_path")
        val NAS_USER        = stringPreferencesKey("nas_user")
        val NAS_PASS        = stringPreferencesKey("nas_pass")
        val NAS_TLS         = booleanPreferencesKey("nas_tls")
        val DEL_AFTER       = booleanPreferencesKey("del_after")
        val METERED         = booleanPreferencesKey("metered")
        val BY_DATE         = booleanPreferencesKey("by_date")
    }

    val flow: Flow<AppSettings> = ctx.dataStore.data.map { p -> p.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        ctx.dataStore.edit { p -> transform(p.toSettings()).write(p) }
    }

    private fun Preferences.toSettings() = AppSettings(
        connectionMode = ConnectionMode.valueOf(
            this[Keys.CONN_MODE] ?: ConnectionMode.WIFI_PTP_IP.name),
        cameraIp     = this[Keys.CAMERA_IP]     ?: "192.168.1.1",
        cameraPort   = this[Keys.CAMERA_PORT]   ?: 15740,
        pollIntervalSec = this[Keys.POLL_INTERVAL] ?: 10,
        uploadJpeg   = this[Keys.UPLOAD_JPEG]   ?: true,
        uploadRaw    = this[Keys.UPLOAD_RAW]    ?: false,
        uploadVideo  = this[Keys.UPLOAD_VIDEO]  ?: false,
        onlyNew      = this[Keys.ONLY_NEW]      ?: true,
        nasProtocol  = NasProtocol.valueOf(this[Keys.NAS_PROTOCOL] ?: NasProtocol.SMB.name),
        nasHost      = this[Keys.NAS_HOST]      ?: "",
        nasPort      = this[Keys.NAS_PORT]      ?: 0,
        nasShare     = this[Keys.NAS_SHARE]     ?: "",
        nasPath      = this[Keys.NAS_PATH]      ?: "/Photos",
        nasUsername  = this[Keys.NAS_USER]      ?: "",
        nasPassword  = this[Keys.NAS_PASS]      ?: "",
        useTls       = this[Keys.NAS_TLS]       ?: false,
        deleteAfterUpload = this[Keys.DEL_AFTER] ?: false,
        runOnMeteredNetwork = this[Keys.METERED] ?: false,
        organizeByDate = this[Keys.BY_DATE]     ?: true
    )

    private fun AppSettings.write(p: androidx.datastore.preferences.core.MutablePreferences) {
        p[Keys.CONN_MODE] = connectionMode.name
        p[Keys.CAMERA_IP] = cameraIp
        p[Keys.CAMERA_PORT] = cameraPort
        p[Keys.POLL_INTERVAL] = pollIntervalSec
        p[Keys.UPLOAD_JPEG] = uploadJpeg
        p[Keys.UPLOAD_RAW] = uploadRaw
        p[Keys.UPLOAD_VIDEO] = uploadVideo
        p[Keys.ONLY_NEW] = onlyNew
        p[Keys.NAS_PROTOCOL] = nasProtocol.name
        p[Keys.NAS_HOST] = nasHost
        p[Keys.NAS_PORT] = nasPort
        p[Keys.NAS_SHARE] = nasShare
        p[Keys.NAS_PATH] = nasPath
        p[Keys.NAS_USER] = nasUsername
        p[Keys.NAS_PASS] = nasPassword
        p[Keys.NAS_TLS] = useTls
        p[Keys.DEL_AFTER] = deleteAfterUpload
        p[Keys.METERED] = runOnMeteredNetwork
        p[Keys.BY_DATE] = organizeByDate
    }
}
