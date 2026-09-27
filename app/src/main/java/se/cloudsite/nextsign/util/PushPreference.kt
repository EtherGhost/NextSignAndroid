package se.cloudsite.nextsign.util

import android.content.Context

// BACKGROUND_ONLY: DocumentPollWorker (Tier 1) runs, no push registration - works
// without any helper app installed, up to ~15+ minutes latency.
// INSTANT: both Tier 1 (as a backup) and Tier 2 (real-time push) run - needs a
// UnifiedPush distributor installed (e.g. ntfy).
enum class NotificationMode { OFF, BACKGROUND_ONLY, INSTANT }

// Per-account: each Nextcloud account known to this app (see AccountHistory) has its
// own notification mode, so e.g. one account can run Instant push while another stays
// Background-only or Off.
object PushPreference {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_LEGACY_MODE = "notification_mode"
    private const val KEY_MIGRATED = "notification_mode_migrated_per_account"
    private fun key(accountName: String) = "notification_mode_$accountName"

    // New accounts default to Background: it works with zero extra setup, unlike
    // Instant, which needs a UnifiedPush distributor already installed.
    private val DEFAULT_MODE = NotificationMode.BACKGROUND_ONLY

    fun getMode(context: Context, accountName: String): NotificationMode {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(accountName), null) ?: return DEFAULT_MODE
        return try {
            NotificationMode.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            DEFAULT_MODE
        }
    }

    fun setMode(context: Context, accountName: String, mode: NotificationMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(accountName), mode.name)
            .apply()
    }

    // One-time upgrade from the single global setting this app had before per-account
    // modes existed: every account already known at that point inherits whatever the
    // old global value was, so nobody's existing choice (e.g. having turned push off)
    // silently resets. Anything added afterward just gets DEFAULT_MODE - there's no
    // reasonable global value left to attribute to a brand new account.
    fun migrateFromLegacyGlobalMode(context: Context, knownAccountNames: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val legacyMode = prefs.getString(KEY_LEGACY_MODE, null)?.let {
            try {
                NotificationMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val editor = prefs.edit()
        if (legacyMode != null) {
            knownAccountNames.forEach { accountName -> editor.putString(key(accountName), legacyMode.name) }
        }
        editor.remove(KEY_LEGACY_MODE)
        editor.putBoolean(KEY_MIGRATED, true)
        editor.apply()
    }
}
