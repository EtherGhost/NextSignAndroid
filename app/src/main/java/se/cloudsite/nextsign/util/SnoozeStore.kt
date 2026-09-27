package se.cloudsite.nextsign.util

import android.content.Context
import android.content.SharedPreferences

// Lets the user silence a still-pending document's badge notification for a while,
// via either the notification's own delete-intent (swipe away) or its explicit
// "Snooze" action button - both lead here. Time-based rather than
// permanent-until-signed, so a forgotten document still resurfaces eventually
// instead of going silent forever.
object SnoozeStore {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_SNOOZED = "snoozed_documents"
    private const val SNOOZE_DURATION_MS = 24 * 60 * 60 * 1000L

    fun snooze(context: Context, uuid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = readEntries(prefs).toMutableMap()
        entries[uuid] = System.currentTimeMillis() + SNOOZE_DURATION_MS
        writeEntries(prefs, entries)
    }

    fun isSnoozed(context: Context, uuid: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = readEntries(prefs)
        val until = entries[uuid] ?: return false
        if (System.currentTimeMillis() >= until) {
            writeEntries(prefs, entries - uuid)
            return false
        }
        return true
    }

    private fun readEntries(prefs: SharedPreferences): Map<String, Long> =
        prefs.getStringSet(KEY_SNOOZED, emptySet()).orEmpty().mapNotNull { entry ->
            val (uuid, until) = entry.split(":", limit = 2).let { it.getOrNull(0) to it.getOrNull(1) }
            val untilMillis = until?.toLongOrNull()
            if (uuid != null && untilMillis != null) uuid to untilMillis else null
        }.toMap()

    private fun writeEntries(prefs: SharedPreferences, entries: Map<String, Long>) {
        val set = entries.map { (uuid, until) -> "$uuid:$until" }.toSet()
        prefs.edit().putStringSet(KEY_SNOOZED, set).apply()
    }
}
