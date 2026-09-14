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

private const val CHANNEL_ID = "nextsign_pending_badge"
private const val NOTIFICATION_TAG = "badge"
private const val PREFS_NAME = "nextsign_settings"
private const val KEY_BADGED_UUIDS = "badged_document_uuids"

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
object PendingSignatureBadges {
    fun sync(context: Context, documents: List<LibreSignDocument>) {
        if (PushPreference.getMode(context) == NotificationMode.OFF) {
            clearAll(context)
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyBadged = prefs.getStringSet(KEY_BADGED_UUIDS, emptySet()).orEmpty()
        val pending = documents.filter { it.canSignNow }
        val pendingUuids = pending.map { it.uuid }.toSet()

        (previouslyBadged - pendingUuids).forEach { uuid ->
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, uuid.hashCode())
        }

        if (hasPostNotificationsPermission(context)) {
            ensureChannel(context)
            pending.forEach { document -> show(context, document) }
        }

        prefs.edit().putStringSet(KEY_BADGED_UUIDS, pendingUuids).apply()
    }

    private fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previouslyBadged = prefs.getStringSet(KEY_BADGED_UUIDS, emptySet()).orEmpty()
        previouslyBadged.forEach { uuid ->
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, uuid.hashCode())
        }
        prefs.edit().remove(KEY_BADGED_UUIDS).apply()
    }

    private fun show(context: Context, document: LibreSignDocument) {
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.poll_new_document_notification, name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
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
