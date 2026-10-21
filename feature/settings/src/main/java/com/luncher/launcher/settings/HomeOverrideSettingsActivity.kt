package com.luncher.launcher.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.luncher.domain.settings.HomeOverridePreferences

/** User-controlled accessibility service state and Home redirect preference. */
class HomeOverrideSettingsActivity : Activity() {

    private lateinit var enabledSwitch: Switch
    private lateinit var status: TextView
    private var updatingSwitch = false
    private val preferences by lazy {
        getSharedPreferences(HomeOverridePreferences.FILE_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_home_override)
        enabledSwitch = findViewById(R.id.home_override_enabled)
        status = findViewById(R.id.home_override_status)
        enabledSwitch.isChecked = preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)
        enabledSwitch.setOnCheckedChangeListener { _, enabled ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            preferences.edit()
                .putBoolean(HomeOverridePreferences.KEY_ENABLED, enabled)
                .putBoolean(HomeOverridePreferences.KEY_SAFETY_STOP, false)
                .putInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, 0)
                .remove(HomeOverridePreferences.KEY_LAST_FAILURE_AT)
                .apply()
            showStatus()
        }
        findViewById<TextView>(R.id.home_override_configure).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.home_override_restore).setOnClickListener {
            // Stop intercepting first, even if the vendor doesn't expose a Home chooser.
            preferences.edit()
                .putBoolean(HomeOverridePreferences.KEY_ENABLED, false)
                .putBoolean(HomeOverridePreferences.KEY_SAFETY_STOP, false)
                .putInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, 0)
                .apply()
            syncEnabledSwitch()
            status.setText(R.string.home_override_restored)
            // Changing Android's default HOME requires an explicit user selection.
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (exception: RuntimeException) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (fallback: RuntimeException) {
                    Toast.makeText(this, R.string.home_override_chooser_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            syncEnabledSwitch()
            showStatus()
        }
    }

    private fun showStatus() {
        val serviceEnabled = isServiceEnabled()
        val redirectEnabled = preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)
        val launcherAvailable = isLauncherXInstalled()
        enabledSwitch.isEnabled = launcherAvailable
        val message = when {
            !launcherAvailable -> R.string.home_override_launcher_missing
            preferences.getBoolean(HomeOverridePreferences.KEY_SAFETY_STOP, false) ->
                R.string.home_override_safety_stop
            !serviceEnabled -> if (redirectEnabled) {
                R.string.home_override_permission_pending
            } else {
                R.string.home_override_service_disabled
            }
            !redirectEnabled -> R.string.home_override_service_enabled_off
            else -> R.string.home_override_unverified
        }
        status.setText(message)
    }

    private fun syncEnabledSwitch() {
        updatingSwitch = true
        try {
            enabledSwitch.isChecked = preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)
        } finally {
            updatingSwitch = false
        }
    }

    private fun isLauncherXInstalled(): Boolean = try {
        packageManager.getApplicationInfo(HomeOverridePreferences.GOOGLE_TV_LAUNCHER_PACKAGE, 0).enabled
    } catch (exception: PackageManager.NameNotFoundException) {
        false
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceName = ComponentName(packageName, SERVICE_CLASS_NAME)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { info -> info.resolveInfo?.serviceInfo?.let { it.packageName == serviceName.packageName && it.name == serviceName.className } == true }
    }

    private companion object {
        const val SERVICE_CLASS_NAME = "com.luncher.launcher.home.RabbitHomeOverrideService"
    }
}
