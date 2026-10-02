package se.cloudsite.nextsign.util

import android.content.Context

// How long the badge notification's "Snooze" action/swipe silences a document for -
// the "Forever" action is separate (see SnoozeStore.FOREVER_MS), not one of these.
enum class SnoozeDuration(val millis: Long) {
    ONE_HOUR(60 * 60 * 1000L),
    FOUR_HOURS(4 * 60 * 60 * 1000L),
    ONE_DAY(24 * 60 * 60 * 1000L),
    THREE_DAYS(3 * 24 * 60 * 60 * 1000L)
}

// Per-account, same as PushPreference - different accounts can want different snooze
// lengths just like they can want different notification modes.
object SnoozeDurationPreference {
    private const val PREFS_NAME = "nextsign_settings"
    private fun key(accountName: String) = "snooze_duration_$accountName"
    private val DEFAULT = SnoozeDuration.ONE_DAY

    fun get(context: Context, accountName: String): SnoozeDuration {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(accountName), null) ?: return DEFAULT
        return try {
            SnoozeDuration.valueOf(stored)
        } catch (e: IllegalArgumentException) {
            DEFAULT
        }
    }

    fun set(context: Context, accountName: String, duration: SnoozeDuration) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(accountName), duration.name)
            .apply()
    }
}
