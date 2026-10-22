package com.luncher.launcher.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.luncher.domain.settings.RabbitConnectPreferences

class RabbitConnectSettingsActivity : Activity() {
    private lateinit var enabled: Switch
    private lateinit var status: TextView
    private lateinit var ip: TextView
    private lateinit var code: TextView
    private var updating = false
    private var lastCodeRefreshRequestAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!isFinishing) { showStatus(); handler.postDelayed(this, 1500) }
        }
    }
    private val prefs by lazy { getSharedPreferences(RabbitConnectPreferences.FILE_NAME, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_rabbit_connect)
        enabled = findViewById(R.id.rabbit_connect_enabled)
        status = findViewById(R.id.rabbit_connect_status)
        ip = findViewById(R.id.rabbit_connect_ip)
        code = findViewById(R.id.rabbit_connect_code)
        enabled.setOnCheckedChangeListener { _, on ->
            if (updating) return@setOnCheckedChangeListener
            if (on) startReceiver() else stopReceiver()
        }
        findViewById<TextView>(R.id.rabbit_connect_accessibility).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show() }
        }
        findViewById<TextView>(R.id.rabbit_connect_overlay_permission).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.rabbit_connect_overlay_ready, Toast.LENGTH_SHORT).show()
            } else try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: ActivityNotFoundException) { Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show() }
        }
        findViewById<TextView>(R.id.rabbit_connect_test).setOnClickListener {
            startServiceAction(RabbitConnectPreferences.ACTION_TEST)
            Toast.makeText(this, R.string.rabbit_connect_test_sent, Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.rabbit_connect_revoke).setOnClickListener {
            startServiceAction(RabbitConnectPreferences.ACTION_REVOKE)
            Toast.makeText(this, R.string.rabbit_connect_revoked, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        showStatus()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startReceiver() {
        prefs.edit().putBoolean(RabbitConnectPreferences.KEY_ENABLED, true).apply()
        startServiceAction(RabbitConnectPreferences.ACTION_START)
        showStatus()
    }

    private fun stopReceiver() {
        prefs.edit().putBoolean(RabbitConnectPreferences.KEY_ENABLED, false).apply()
        startServiceAction(RabbitConnectPreferences.ACTION_STOP)
        showStatus()
    }

    private fun startServiceAction(action: String) {
        val intent = Intent().setClassName(packageName, RabbitConnectPreferences.SERVICE_CLASS).setAction(action)
        try { ContextCompat.startForegroundService(this, intent) }
        catch (_: RuntimeException) { Toast.makeText(this, R.string.rabbit_connect_service_error, Toast.LENGTH_SHORT).show() }
    }

    private fun showStatus() {
        if (!::enabled.isInitialized) return
        updating = true
        enabled.isChecked = prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false)
        updating = false
        val statusText = when (prefs.getString(RabbitConnectPreferences.KEY_STATUS, "disabled")) {
            "listening" -> getString(R.string.rabbit_connect_listening)
            "waiting_pair" -> getString(R.string.rabbit_connect_waiting_pair)
            "connected" -> getString(R.string.rabbit_connect_connected)
            "authenticating" -> getString(R.string.rabbit_connect_authenticating)
            "no_network" -> getString(R.string.rabbit_connect_no_network)
            "overlay_unavailable" -> getString(R.string.rabbit_connect_overlay_unavailable)
            "test_shown" -> getString(R.string.rabbit_connect_test_ready)
            "disabled" -> getString(R.string.rabbit_connect_disabled)
            "stopped" -> getString(R.string.rabbit_connect_stopped)
            "error" -> getString(R.string.rabbit_connect_error)
            else -> getString(R.string.rabbit_connect_starting)
        }
        status.text = getString(R.string.rabbit_connect_status, statusText + if (accessibilityEnabled()) " · accesibilidad disponible" else "")
        val address = prefs.getString(RabbitConnectPreferences.KEY_IP, null).orEmpty().ifBlank { getString(R.string.rabbit_connect_no_ip) }
        ip.text = getString(R.string.rabbit_connect_ip, address, com.rabbit.connect.protocol.RabbitConnectProtocol.PORT)
        val paired = prefs.getBoolean(RabbitConnectPreferences.KEY_PAIRED, false)
        val expires = prefs.getLong(RabbitConnectPreferences.KEY_CODE_EXPIRES_AT, 0L)
        val tempCode = prefs.getString(RabbitConnectPreferences.KEY_PAIRING_CODE, null).orEmpty()
        val codeValid = tempCode.isNotBlank() && expires > System.currentTimeMillis()
        code.text = if (paired) getString(R.string.rabbit_connect_paired)
            else getString(R.string.rabbit_connect_code, if (codeValid) tempCode else getString(R.string.rabbit_connect_code_refreshing))
        if (!paired && prefs.getBoolean(RabbitConnectPreferences.KEY_ENABLED, false) && !codeValid &&
            SystemClock.elapsedRealtime() - lastCodeRefreshRequestAt >= CODE_REFRESH_RETRY_MS) {
            lastCodeRefreshRequestAt = SystemClock.elapsedRealtime()
            startServiceAction(RabbitConnectPreferences.ACTION_START)
        }
    }

    companion object {
        private const val CODE_REFRESH_RETRY_MS = 5_000L
    }

    private fun accessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val service = ComponentName(packageName, "com.luncher.launcher.home.RabbitHomeOverrideService")
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC).any {
            it.resolveInfo?.serviceInfo?.let { info -> info.packageName == service.packageName && info.name == service.className } == true
        }
    }
}
