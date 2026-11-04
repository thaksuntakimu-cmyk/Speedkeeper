package com.example.speedkeeper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

class SpeedService : Service() {

    companion object {
        const val ACTION_SPEED = "com.example.speedkeeper.SPEED"
        const val EXTRA_MBPS = "mbps"          // MB/s, -1 = error, -2 = waiting for Wi-Fi
        @Volatile var running = false

        private const val CHANNEL = "speed"
        private const val TEST_URL = "https://speed.cloudflare.com/__down?bytes=10000000"
        private const val PAUSE_MS = 1000L     // বাড়ালে ডাটা কম খরচ হবে
    }

    private var worker: Thread? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Speed test", NotificationManager.IMPORTANCE_LOW)
        )
        val n = buildNotification("Starting...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }

        // Wi-Fi কে ঘুমাতে না দেওয়ার জন্য
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                   else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(mode, "speedkeeper:wifi").apply { acquire() }

        worker = Thread { loop(nm) }.also { it.start() }
        return START_STICKY
    }

    private fun loop(nm: NotificationManager) {
        while (running) {
            try {
                if (!onWifi()) {                 // মোবাইল ডাটা বাঁচাতে শুধু Wi-Fi তে চলবে
                    publish(nm, -2.0)
                    Thread.sleep(3000)
                    continue
                }
                val start = SystemClock.elapsedRealtime()
                val c = URL(TEST_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 10000
                var total = 0L
                c.inputStream.use { s ->
                    val buf = ByteArray(32 * 1024)
                    while (running) {
                        val r = s.read(buf)
                        if (r < 0) break
                        total += r
                    }
                }
                c.disconnect()
                val sec = (SystemClock.elapsedRealtime() - start) / 1000.0
                if (sec > 0) publish(nm, total / 1_000_000.0 / sec)
                Thread.sleep(PAUSE_MS)
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                publish(nm, -1.0)
                try { Thread.sleep(3000) } catch (_: InterruptedException) { break }
            }
        }
    }

    private fun onWifi(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun publish(nm: NotificationManager, mbps: Double) {
        val text = when {
            mbps == -2.0 -> "Wi-Fi এর জন্য অপেক্ষা..."
            mbps < 0 -> "Error, আবার চেষ্টা করছে..."
            else -> String.format("%.2f MB/s", mbps)
        }
        nm.notify(1, buildNotification(text))
        sendBroadcast(Intent(ACTION_SPEED).setPackage(packageName).putExtra(EXTRA_MBPS, mbps))
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("Speed Keeper চলছে")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        wifiLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
