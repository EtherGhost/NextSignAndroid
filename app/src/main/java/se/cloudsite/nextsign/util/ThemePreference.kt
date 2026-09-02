package se.cloudsite.nextsign.util

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Plain SharedPreferences, not DataStore - this app has no other persistence yet (the
// document list is refreshed from the network on demand, no local database), and a
// single string preference doesn't warrant pulling in a new persistence layer.
object ThemePreference {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun get(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }
}
