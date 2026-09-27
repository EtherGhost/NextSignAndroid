package se.cloudsite.nextsign.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import se.cloudsite.nextsign.MainActivity
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.util.NotificationMode
import se.cloudsite.nextsign.util.PushPreference
import se.cloudsite.nextsign.util.SnoozeStore

private const val CHANNEL_ID = "nextsign_pending_badge"
private const val NOTIFICATION_TAG = "badge"
private const val PREFS_NAME = "nextsign_settings"

// The launcher icon badge count on essentially every modern launcher (stock/Pixel
// included) is derived from the app's own currently-active notification count, by
// design since Android 8 - there is no separate "just set a number" API a well-behaved
// app can call instead. So one low-priority, silent, non-alerting notification per
// document that still needs this signer's signature (on its own channel, tagged
// "badge" so it can never collide with DocumentPollWorker/PushServiceImpl's own "hey,
// something new arrived" alert notifications, which use the default tag) IS the badge.
//
// sync() must run on every successful document-list load - both MainActivity's
// foreground refresh() and DocumentPollWorker's periodic check - not only when
// something new arrives, since a document leaving canSignNow (signed elsewhere,
// deleted, no longer assigned to this signer) needs its badge notification canceled
// even when the "new arrival" diffing logic elsewhere has nothing to react to.
//
// Per-account: the "currently badged" bookkeeping is stored separately per account,
// since it's replaced wholesale on every sync() call - sharing one flat set across
// accounts would make syncing account B's documents cancel account A's just-shown
// badges too (both calls can happen back to back in the same poll cycle).
object PendingSignatureBadges {
    private fun badgedUuidsKey(accountName: String) = "badged_document_uuids_$accountName"

    fun sync(context: Context, accountName: String, documents: List<LibreSignDocument>) {
        if (PushPreference.getMode(context, accountName) == NotificationMode.OFF) {
            clearAll(context, accountName)
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = badgedUuidsKey(accountName)
        val previouslyBadged = prefs.getStringSet(key, emptySet()).orEmpty()
        val pending = documents.filter { it.canSignNow }
        val activelyShown = pending.filterNot { SnoozeStore.isSnoozed(context, it.uuid) }
        val activelyShownUuids = activelyShown.map { it.uuid }.toSet()

        // Covers both documents that stopped being pending and documents that just got
        // snoozed - either way no notification should remain for them.
        (previouslyBadged - activelyShownUuids).forEach { uuid ->
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, uuid.hashCode())
        }

        if (hasPostNotificationsPermission(context)) {
            ensureChannel(context)
            activelyShown.forEach { document -> show(context, accountName, document) }
        }

        prefs.edit().putStringSet(key, activelyShownUuids).apply()
    }

    fun cancelBadge(context: Context, accountName: String, uuid: String) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, uuid.hashCode())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = badgedUuidsKey(accountName)
        val previouslyBadged = prefs.getStringSet(key, emptySet()).orEmpty()
        prefs.edit().putStringSet(key, previouslyBadged - uuid).apply()
    }

    private fun clearAll(context: Context, accountName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = badgedUuidsKey(accountName)
        val previouslyBadged = prefs.getStringSet(key, emptySet()).orEmpty()
        previouslyBadged.forEach { uuid ->
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, uuid.hashCode())
        }
        prefs.edit().remove(key).apply()
    }

    private fun show(context: Context, accountName: String, document: LibreSignDocument) {
        val name = document.name.ifEmpty { context.getString(R.string.document_untitled) }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            document.uuid.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = Intent(context, SnoozeActionReceiver::class.java).apply {
            putExtra(EXTRA_DOCUMENT_UUID, document.uuid)
            putExtra(EXTRA_ACCOUNT_NAME, accountName)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            document.uuid.hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.poll_new_document_notification, name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            // Both a swipe (deleteIntent) and the explicit action button lead to the same
            // 24h snooze, since a plain swipe is the universal Android expectation for
            // "stop showing me this," not just the button.
            .setDeleteIntent(snoozePendingIntent)
            .addAction(0, context.getString(R.string.notification_action_snooze), snoozePendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, document.uuid.hashCode(), notification)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_badge_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.setShowBadge(true)
                manager.createNotificationChannel(channel)
            }
        }
    }
}
