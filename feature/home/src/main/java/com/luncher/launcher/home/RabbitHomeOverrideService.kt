package com.luncher.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.luncher.domain.settings.HomeOverridePreferences
import java.util.Locale

/**
 * Best-effort bridge from Google TV Home's foreground window to Rabbit TV. The service observes
 * only window-state events from LauncherX and never reads view text or content from other apps.
 */
class RabbitHomeOverrideService : AccessibilityService() {

    private var lastLaunchRequestAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != HomeOverridePreferences.GOOGLE_TV_LAUNCHER_PACKAGE) return
        if (!getSharedPreferences(HomeOverridePreferences.FILE_NAME, MODE_PRIVATE)
                .getBoolean(HomeOverridePreferences.KEY_ENABLED, false)
        ) return

        val className = event.className?.toString().orEmpty().lowercase(Locale.ROOT)
        if (className.contains("dialog") || className.contains("popup") ||
            className.contains("settings") || className.contains("menu")
        ) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchRequestAt < REDIRECT_COOLDOWN_MS) return
        lastLaunchRequestAt = now

        try {
            startActivity(
                Intent(this, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        } catch (exception: RuntimeException) {
            // Background-activity policy may reject this on a given firmware without throwing.
            Log.w(TAG, "Could not request Rabbit TV after Google TV Home appeared", exception)
        }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "RabbitHomeOverride"
        const val REDIRECT_COOLDOWN_MS = 1_500L
    }
}
