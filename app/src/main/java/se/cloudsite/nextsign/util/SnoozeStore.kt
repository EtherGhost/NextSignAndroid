package se.cloudsite.nextsign.util

import android.content.Context
import android.content.SharedPreferences

// Lets the user silence a still-pending document's badge notification for a while,
// via the notification's own delete-intent (swipe away), its "Snooze" action button
// (both use the account's configured SnoozeDuration), or its "Forever" action button
// (FOREVER_MS - silenced until the document's own state changes, e.g. it gets signed,
// rather than resurfacing on a timer).
object SnoozeStore {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_SNOOZED = "snoozed_documents"
    const val FOREVER_MS = Long.MAX_VALUE

    fun snooze(context: Context, uuid: String, durationMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = readEntries(prefs).toMutableMap()
        entries[uuid] = if (durationMillis == FOREVER_MS) {
            FOREVER_MS
        } else {
            System.currentTimeMillis() + durationMillis
        }
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
