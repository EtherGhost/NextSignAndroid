package se.cloudsite.nextsign.util

import android.content.Context

// BACKGROUND_ONLY: DocumentPollWorker (Tier 1) runs, no push registration - works
// without any helper app installed, up to ~15+ minutes latency.
// INSTANT: both Tier 1 (as a backup) and Tier 2 (real-time push) run - needs a
// UnifiedPush distributor installed (e.g. ntfy).
// Defaults to INSTANT - matches the app's behavior before this tri-state setting
// existed (push registration ran unconditionally on sign-in).
enum class NotificationMode { OFF, BACKGROUND_ONLY, INSTANT }

object PushPreference {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_NOTIFICATION_MODE = "notification_mode"

    fun getMode(context: Context): NotificationMode {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NOTIFICATION_MODE, null) ?: return NotificationMode.INSTANT
        return try {
            NotificationMode.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            NotificationMode.INSTANT
        }
    }

    fun setMode(context: Context, mode: NotificationMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFICATION_MODE, mode.name)
            .apply()
    }
}
