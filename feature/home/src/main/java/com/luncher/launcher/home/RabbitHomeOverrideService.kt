package com.luncher.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.luncher.domain.settings.HomeOverridePreferences
import java.util.ArrayDeque
import java.util.Locale

/**
 * Optional, user-enabled bridge from Google TV Home (LauncherX) to Rabbit TV.
 * Only LauncherX window state is observed; no window content is read.
 *
 * The Activity acknowledges a successful foreground transition in-process. An unsuccessful
 * startActivity can fail silently under Android's background activity launch policy, so we
 * stop redirecting after repeated unacknowledged attempts or suspiciously frequent redirects.
 */
class RabbitHomeOverrideService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences by lazy {
        getSharedPreferences(HomeOverridePreferences.FILE_NAME, MODE_PRIVATE)
    }
    private val recentRequests = ArrayDeque<Long>()
    private var lastRequestAt = 0L
    private var pendingRequestAt = 0L

    private val verifyRequest = Runnable {
        val requestedAt = pendingRequestAt
        pendingRequestAt = 0L
        if (requestedAt == 0L || !preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)) {
            return@Runnable
        }
        if (HomeOverrideRuntime.lastHomeResumedAt >= requestedAt) {
            preferences.edit()
                .putInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, 0)
                .remove(HomeOverridePreferences.KEY_LAST_FAILURE_AT)
                .apply()
        } else {
            recordFailedAttempt()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != HomeOverridePreferences.GOOGLE_TV_LAUNCHER_PACKAGE) return
        if (!preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)) return

        // Never redirect from a system dialog, launcher menu or launcher settings surface.
        val className = event.className?.toString().orEmpty().lowercase(Locale.ROOT)
        if (className.contains("dialog") || className.contains("popup") ||
            className.contains("settings") || className.contains("menu")
        ) return

        val now = SystemClock.elapsedRealtime()
        if (pendingRequestAt != 0L || now - lastRequestAt < REQUEST_COOLDOWN_MS) return
        lastRequestAt = now

        while (!recentRequests.isEmpty() && now - recentRequests.first > REQUEST_WINDOW_MS) {
            recentRequests.removeFirst()
        }
        if (recentRequests.size >= MAX_REQUESTS_IN_WINDOW) {
            disableForSafety("LauncherX and Rabbit TV appear to be cycling")
            return
        }
        recentRequests.addLast(now)
        pendingRequestAt = now

        try {
            startActivity(
                Intent(this, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
            // Android may silently reject a background Activity start; check actual onResume.
            mainHandler.postDelayed(verifyRequest, VERIFY_DELAY_MS)
        } catch (exception: RuntimeException) {
            pendingRequestAt = 0L
            Log.w(TAG, "Rabbit TV launch request failed", exception)
            recordFailedAttempt()
        }
    }

    private fun recordFailedAttempt() {
        if (!preferences.getBoolean(HomeOverridePreferences.KEY_ENABLED, false)) return
        val now = System.currentTimeMillis()
        val previousAt = preferences.getLong(HomeOverridePreferences.KEY_LAST_FAILURE_AT, 0L)
        val previousCount = if (now >= previousAt && now - previousAt <= FAILURE_WINDOW_MS) {
            preferences.getInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, 0)
        } else {
            0
        }
        val count = previousCount + 1
        preferences.edit()
            .putInt(HomeOverridePreferences.KEY_FAILED_ATTEMPTS, count)
            .putLong(HomeOverridePreferences.KEY_LAST_FAILURE_AT, now)
            .apply()
        if (count >= MAX_FAILED_ATTEMPTS) {
            disableForSafety("Activity did not reach foreground after $count requests")
        }
    }

    private fun disableForSafety(reason: String) {
        preferences.edit()
            .putBoolean(HomeOverridePreferences.KEY_ENABLED, false)
            .putBoolean(HomeOverridePreferences.KEY_SAFETY_STOP, true)
            .apply()
        recentRequests.clear()
        mainHandler.removeCallbacks(verifyRequest)
        pendingRequestAt = 0L
        Log.w(TAG, "Home override disabled for safety: $reason")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(verifyRequest)
        pendingRequestAt = 0L
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RabbitHomeOverride"
        const val REQUEST_COOLDOWN_MS = 1_500L
        const val VERIFY_DELAY_MS = 4_000L
        const val MAX_FAILED_ATTEMPTS = 3
        const val FAILURE_WINDOW_MS = 90_000L
        const val REQUEST_WINDOW_MS = 30_000L
        const val MAX_REQUESTS_IN_WINDOW = 6
    }
}

/** In-process acknowledgement; automatically resets across process/device restarts. */
internal object HomeOverrideRuntime {
    @Volatile var lastHomeResumedAt: Long = 0L
}
