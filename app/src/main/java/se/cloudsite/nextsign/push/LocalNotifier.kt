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

private const val CHANNEL_ID = "nextsign_notifications"

// Shared by both delivery paths (real-time push via PushServiceImpl, and the
// periodic background poll fallback via DocumentPollWorker) so they use one Android
// notification channel, not two the user would have to manage separately in system
// settings. Tapping opens MainActivity (launchMode singleTask, see the manifest) -
// onNewIntent() there triggers a refresh, so the list is current even though this
// can't deep-link to the specific document (see the push notifications plan doc:
// Nextcloud's push payload only carries LibreSign's internal numeric SignRequest id,
// which nothing in this app currently has a lookup for).
object LocalNotifier {
    fun show(context: Context, text: String, notificationId: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NextSign")
            .setContentText(text.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Notifications", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }
}
