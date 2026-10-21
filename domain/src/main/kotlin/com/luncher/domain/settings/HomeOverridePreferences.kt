package com.luncher.domain.settings

/** Shared local preference contract for the optional, reversible LauncherX override. */
object HomeOverridePreferences {
    const val FILE_NAME = "rabbit_home_override"
    const val KEY_ENABLED = "enabled"
    const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    const val KEY_LAST_FAILURE_AT = "last_failure_at"
    const val KEY_SAFETY_STOP = "safety_stop"
    const val GOOGLE_TV_LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx"
}
