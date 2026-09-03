package se.cloudsite.nextsign.util

import android.content.Context

// Tracks which document UUIDs have already been shown to the user (foreground list
// load or a prior background poll), so DocumentPollWorker only notifies about
// genuinely new arrivals - and, critically, so the very first poll after this feature
// ships doesn't flood the user with notifications for every pre-existing document.
// Updated from both MainActivity's own refresh() (foreground) and the background
// worker (Tier 1 of the push notifications plan), so seeing something in the app
// counts the same as having been notified about it already.
object SeenDocumentsStore {
    private const val PREFS_NAME = "nextsign_settings"
    private const val KEY_SEEN_UUIDS = "seen_document_uuids"
    private const val KEY_INITIALIZED = "seen_document_uuids_initialized"

    fun isInitialized(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INITIALIZED, false)

    fun getSeen(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_SEEN_UUIDS, emptySet())
            .orEmpty()

    fun markSeen(context: Context, uuids: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SEEN_UUIDS, uuids)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }
}
