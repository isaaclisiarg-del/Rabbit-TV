package com.luncher.launcher.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
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
    private lateinit var defaultStatus: TextView
    private val preferences by lazy {
        getSharedPreferences(HomeOverridePreferences.FILE_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_home_override)
        enabledSwitch = findViewById(R.id.home_override_enabled)
        status = findViewById(R.id.home_override_status)
        defaultStatus = findViewById(R.id.home_override_default_status)
        enabledSwitch.isChecked = preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)
        enabledSwitch.setOnCheckedChangeListener { _, enabled ->
            preferences.edit().putBoolean(HomeOverridePreferences.KEY_ENABLED, enabled).apply()
            showStatus()
        }
        findViewById<TextView>(R.id.home_override_configure).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.home_override_choose_home).setOnClickListener { openHomeSelector() }
        findViewById<TextView>(R.id.home_override_restore).setOnClickListener {
            preferences.edit().putBoolean(HomeOverridePreferences.KEY_ENABLED, false).apply()
            enabledSwitch.isChecked = false
            status.setText(R.string.home_override_restored)
            openHomeSelector()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            showStatus()
            showDefaultHome()
        }
    }

    private fun showStatus() {
        val serviceEnabled = isServiceEnabled()
        val redirectEnabled = preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)
        val message = when {
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

    private fun showDefaultHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = try {
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: RuntimeException) {
            null
        }
        val activity = resolved?.activityInfo
        when {
            activity == null -> defaultStatus.setText(R.string.home_override_default_unavailable)
            activity.packageName == packageName -> defaultStatus.setText(R.string.home_override_default_rabbit)
            activity.name.contains("ResolverActivity") || activity.name.contains("ChooserActivity") ->
                defaultStatus.setText(R.string.home_override_default_unavailable)
            else -> {
                val appName = try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(activity.packageName, 0))
                } catch (_: PackageManager.NameNotFoundException) {
                    activity.loadLabel(packageManager)
                }
                defaultStatus.text = getString(R.string.home_override_default_named, appName)
            }
        }
    }

    private fun openHomeSelector() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            return
        } catch (_: ActivityNotFoundException) {
            // Older and vendor TV firmware may omit the HOME settings page.
        } catch (_: SecurityException) {
            // Fall back to the system HOME resolver when the settings page is restricted.
        }
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
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
