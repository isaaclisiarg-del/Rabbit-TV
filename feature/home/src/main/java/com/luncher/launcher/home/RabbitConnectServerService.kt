package com.luncher.launcher.home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.luncher.domain.settings.RabbitConnectPreferences
import com.rabbit.connect.protocol.RabbitConnectProtocol
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Collections
import java.util.LinkedHashMap

class RabbitConnectServerService : Service() {
    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: java.net.Socket? = null
    private var worker: Thread? = null
    private val prefs by lazy { getSharedPreferences(RabbitConnectPreferences.FILE_NAME, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Rabbit Connect")
            .setContentText("Receptor local disponible")
            .setOngoing(true).setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            RabbitConnectPreferences.ACTION_STOP -> {
                prefs.edit().putBoolean(RabbitConnectPreferences.KEY_ENABLED, false)
                    .remove(RabbitConnectPreferences.KEY_PAIRING_CODE).remove(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT).apply()
                runCatching { client?.close() }
                runCatching { server?.close() }
                stopSelf()
                return START_NOT_STICKY
            }
            RabbitConnectPreferences.ACTION_REVOKE -> {
                RabbitConnectSecretStore(this).clear()
                prefs.edit().remove(RabbitConnectPreferences.KEY_PAIRING_CODE).remove(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT).apply()
                runCatching { client?.close() }
                if (!prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false)) { stopSelf(); return START_NOT_STICKY }
                pairingCode()
            }
            RabbitConnectPreferences.ACTION_TEST -> {
                val shown = RabbitConnectOverlay.show(this, "tv-synthetic-${System.currentTimeMillis()}", "Rabbit TV", "Prueba de Rabbit Connect", "Superposición sintética · 6 segundos")
                prefs.edit().putString(RabbitConnectPreferences.KEY_STATUS, if (shown) "test_shown" else "overlay_unavailable").apply()
                if (!prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false)) stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                prefs.edit().putBoolean(RabbitConnectPreferences.KEY_ENABLED, true).apply()
                pairingCode()
            }
        }
        if (worker?.isAlive != true) {
            running = true
            worker = Thread(::serverLoop, "RabbitConnectServer").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        runCatching { client?.close() }
        server?.close()
        worker?.interrupt()
        worker = null
        prefs.edit().putString(RabbitConnectPreferences.KEY_STATUS,
            if (prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false)) "stopped" else "disabled").apply()
        super.onDestroy()
    }

    private fun serverLoop() {
        val keys = RabbitConnectSecretStore(this)
        while (running && prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false)) {
            try {
                val address = localIpv4()
                prefs.edit().putString(RabbitConnectPreferences.KEY_IP, address.orEmpty()).apply()
                if (address.isNullOrBlank()) {
                    status("no_network")
                    Thread.sleep(1500)
                    continue
                }
                val listener = ServerSocket(RabbitConnectProtocol.PORT, 4, InetAddress.getByName("0.0.0.0"))
                listener.soTimeout = 1500
                server = listener
                status(if (keys.load() == null) "waiting_pair" else "listening")
                while (running && prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false) && !listener.isClosed) {
                    try {
                        listener.accept().use { socket ->
                            client = socket
                            socket.soTimeout = 8000
                            status("authenticating")
                            try {
                                RabbitConnectProtocol.accept(socket, keys, { pairingCode() }, { status("connected") }) { bytes -> handle(bytes) }
                                if (keys.load() != null) {
                                    prefs.edit().putString(RabbitConnectPreferences.KEY_STATUS, "listening")
                                        .remove(RabbitConnectPreferences.KEY_PAIRING_CODE)
                                        .remove(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT).apply()
                                } else {
                                    pairingCode()
                                    status("waiting_pair")
                                }
                            } catch (failure: Exception) {
                                if (keys.load() == null) failPairingAttempt()
                                status(if (keys.load() == null) "waiting_pair" else "listening")
                            } finally {
                                client = null
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        localIpv4()?.let { prefs.edit().putString(RabbitConnectPreferences.KEY_IP, it).apply() }
                        if (keys.load() == null) pairingCode()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (_: Exception) {
                status("error")
                try { Thread.sleep(2000) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); break }
            } finally {
                runCatching { server?.close() }
                server = null
            }
        }
    }

    private fun handle(bytes: ByteArray): ByteArray? {
        val message = JSONObject(String(bytes, Charsets.UTF_8))
        val kind = message.optString("type")
        val id = message.optString("id").take(128)
        if (kind == "revoke") {
            RabbitConnectSecretStore(this).clear()
            prefs.edit().remove(RabbitConnectPreferences.KEY_PAIRING_CODE).putLong(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT, 0L).apply()
            pairingCode()
            status("waiting_pair")
            return ack(id, true, "revoke")
        }
        if (kind == "ping") return ack(id, true)
        if (kind != "notice" && kind != "test") return ack("", false)
        if (!id.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) return ack("", false)
        val app = message.optString("app").take(180)
        val title = message.optString("title").take(256)
        val text = message.optString("text").take(512)
        val shown = RabbitConnectOverlay.show(this, id, app, title, text)
        if (!shown) status("overlay_unavailable")
        return ack(id, shown)
    }

    private fun ack(id: String, shown: Boolean, action: String = "") = JSONObject().put("type", "ack").put("id", id).put("shown", shown)
        .put("action", action).toString().toByteArray(Charsets.UTF_8)

    private fun pairingCode(): String {
        if (RabbitConnectSecretStore(this).load() != null) return ""
        val old = prefs.getString(RabbitConnectPreferences.KEY_PAIRING_CODE, null)
        val expiresAt = prefs.getLong(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT, 0L)
        if (!old.isNullOrBlank() && expiresAt > System.currentTimeMillis()) return old
        val digits = buildString { repeat(12) { append(random.nextInt(10)) } }
        prefs.edit().putString(RabbitConnectPreferences.KEY_PAIRING_CODE, digits)
            .putLong(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT, System.currentTimeMillis() + CODE_LIFETIME_MS).apply()
        failedAttempts = 0
        return digits
    }

    private fun failPairingAttempt() {
        failedAttempts++
        if (failedAttempts >= MAX_PAIR_FAILURES) {
            prefs.edit().remove(RabbitConnectPreferences.KEY_PAIRING_CODE).putLong(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT, 0L).apply()
            pairingCode()
        }
    }

    private fun localIpv4(): String? = runCatching {
        val all = Collections.list(NetworkInterface.getNetworkInterfaces())
        all.asSequence().filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { when { it.name.startsWith("wlan", true) || it.name.contains("wifi", true) -> 0; it.name.startsWith("eth", true) -> 1; else -> 2 } }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private fun status(value: String) { prefs.edit().putString(RabbitConnectPreferences.KEY_STATUS, value).apply() }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Rabbit Connect", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null); enableVibration(false); description = "Receptor local de avisos de Rabbit Sentinel"
            })
    }

    companion object {
        private const val CHANNEL = "rabbit_connect_receiver"
        private const val NOTIFICATION_ID = 4812
        private const val CODE_LIFETIME_MS = 5 * 60 * 1000L
        private const val MAX_PAIR_FAILURES = 5
        private val random = SecureRandom()
        @Volatile private var failedAttempts = 0
    }
}
