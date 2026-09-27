package se.cloudsite.nextsign.util

import android.content.Context

// Tracks which document UUIDs have already been shown to the user (foreground list
// load or a prior background poll), so DocumentPollWorker only notifies about
// genuinely new arrivals - and, critically, so the very first poll after this feature
// ships doesn't flood the user with notifications for every pre-existing document.
// Updated from both MainActivity's own refresh() (foreground) and the background
// worker (Tier 1 of the push notifications plan), so seeing something in the app
// counts the same as having been notified about it already.
//
// Per-account: each account's seen-set is stored separately, since markSeen() replaces
// the whole set on every call - sharing one flat set across accounts would mean
// polling account B wipes out account A's just-recorded seen state.
object SeenDocumentsStore {
    private const val PREFS_NAME = "nextsign_settings"
    private fun seenKey(accountName: String) = "seen_document_uuids_$accountName"
    private fun initializedKey(accountName: String) = "seen_document_uuids_initialized_$accountName"

    fun isInitialized(context: Context, accountName: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(initializedKey(accountName), false)

    fun getSeen(context: Context, accountName: String): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(seenKey(accountName), emptySet())
            .orEmpty()

    fun markSeen(context: Context, accountName: String, uuids: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(seenKey(accountName), uuids)
            .putBoolean(initializedKey(accountName), true)
            .apply()
    }
}
