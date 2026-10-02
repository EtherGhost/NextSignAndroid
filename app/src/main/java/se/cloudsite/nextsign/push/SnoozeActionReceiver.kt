package se.cloudsite.nextsign.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.cloudsite.nextsign.util.SnoozeDuration
import se.cloudsite.nextsign.util.SnoozeStore

const val EXTRA_DOCUMENT_UUID = "document_uuid"
const val EXTRA_ACCOUNT_NAME = "account_name"
const val EXTRA_SNOOZE_DURATION_MILLIS = "snooze_duration_millis"

// Fired by the badge notification's "Snooze" action button, its delete-intent (a
// plain swipe - same duration as the button), and its "Forever" action button (passes
// SnoozeStore.FOREVER_MS instead) - see PendingSignatureBadges.
class SnoozeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uuid = intent.getStringExtra(EXTRA_DOCUMENT_UUID) ?: return
        val accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME) ?: return
        val durationMillis = intent.getLongExtra(EXTRA_SNOOZE_DURATION_MILLIS, SnoozeDuration.ONE_DAY.millis)
        SnoozeStore.snooze(context, uuid, durationMillis)
        PendingSignatureBadges.cancelBadge(context, accountName, uuid)
    }
}
