package com.camtonas.app.camera

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Helpers for finding a PTP/IP camera on the local network.
 *
 * Nikon cameras in WiFi AP mode use 192.168.1.1 (older) or 192.168.0.1
 * (some). We try a small list of common addresses on the standard PTP
 * port 15740. If you can configure your camera with a static IP, the
 * setting in [com.camtonas.app.data.AppSettings.cameraIp] takes
 * precedence and the discovery is skipped.
 */
object CameraDiscovery {

    private val CANDIDATE_IPS = listOf(
        "192.168.1.1",
        "192.168.0.1",
        "192.168.1.2",
        "192.168.0.2"
    )

    data class Found(val host: String, val port: Int)

    suspend fun discover(port: Int = 15740, timeoutMs: Int = 1500): List<Found> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<Found>()
            for (ip in CANDIDATE_IPS) {
                if (probe(ip, port, timeoutMs)) out += Found(ip, port)
            }
            out
        }

    private fun probe(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (e: Throwable) { false }

    /** Best-effort: try to figure out the gateway address (the camera's IP in AP mode). */
    @Suppress("DEPRECATION")
    fun guessCameraIp(context: Context): String? {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val dhcp = wm.dhcpInfo ?: return null
            val gw = dhcp.gateway
            // gateway address is an Int packed in little-endian
            val ip = Inet4Address.getByAddress(
                byteArrayOf(
                    (gw and 0xFF).toByte(),
                    ((gw shr 8) and 0xFF).toByte(),
                    ((gw shr 16) and 0xFF).toByte(),
                    ((gw shr 24) and 0xFF).toByte()
                )
            ).hostAddress
            ip
        } catch (e: Throwable) {
            Log.w("CamToNas", "guessCameraIp failed", e); null
        }
    }
}
