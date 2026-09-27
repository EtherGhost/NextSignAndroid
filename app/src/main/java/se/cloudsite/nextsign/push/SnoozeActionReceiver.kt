package se.cloudsite.nextsign.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.cloudsite.nextsign.util.SnoozeStore

const val EXTRA_DOCUMENT_UUID = "document_uuid"

// Fired both by the badge notification's "Snooze" action button and by its
// delete-intent (a plain swipe) - see PendingSignatureBadges. Either way the
// document goes quiet for the same 24h.
class SnoozeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uuid = intent.getStringExtra(EXTRA_DOCUMENT_UUID) ?: return
        SnoozeStore.snooze(context, uuid)
        PendingSignatureBadges.cancelBadge(context, uuid)
    }
}
