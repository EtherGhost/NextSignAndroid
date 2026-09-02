package se.cloudsite.nextsign.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import se.cloudsite.nextsign.MainActivity
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.network.ApiProvider

private const val TAG = "NextSignPush"
private const val CHANNEL_ID = "nextsign_push_test"
private const val NOTIFICATION_ID = 9001

// UnifiedPush proof-of-concept (Tier 2 of the push notifications plan) - receives
// real Nextcloud WebPush notifications directly via whichever distributor the user
// has installed (e.g. ntfy). No bridge/server of our own: Nextcloud sends encrypted
// pushes straight to the distributor's own public endpoint, and this library decrypts
// them on-device before onMessage() is called. Confirmed against Nextcloud's own
// WebPushController.php source and Nextcloud Talk's real Android client, not guessed.
class PushServiceImpl : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val pubKeySet = endpoint.pubKeySet
        if (pubKeySet == null) {
            Log.w(TAG, "onNewEndpoint: no web push key set on the endpoint, cannot register")
            return
        }
        withAccount { account ->
            val api = ApiProvider.getNotificationsApi(applicationContext, account)
            val response = api.registerWebPush(endpoint.url, pubKeySet.pubKey, pubKeySet.auth, "all").execute()
            Log.i(TAG, "registerWebPush: HTTP ${response.code()}")
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        val content = message.content.toString(Charsets.UTF_8)
        val json = try {
            JSONObject(content)
        } catch (e: Exception) {
            null
        }
        val activationToken = json?.optString("activationToken", "")?.ifEmpty { null }
        if (activationToken != null) {
            withAccount { account ->
                val api = ApiProvider.getNotificationsApi(applicationContext, account)
                val response = api.activateWebPush(activationToken).execute()
                Log.i(TAG, "activateWebPush: HTTP ${response.code()}")
            }
            return
        }
        Log.i(TAG, "onMessage: $content")
        // Nextcloud's push payload is deliberately minimal (Push.php's encodeNotif(),
        // capped near 240 bytes pre-encryption): {"nid","app","subject","type","id"} -
        // no separate message/body field. "subject" is the one human-readable string;
        // this proof of concept just shows it directly rather than fetching full
        // notification details from the server.
        val subject = json?.optString("subject", "")?.ifEmpty { null }
        showNotification(subject ?: content)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "UnifiedPush registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        Log.i(TAG, "UnifiedPush unregistered")
    }

    private fun withAccount(block: (com.nextcloud.android.sso.model.SingleSignOnAccount) -> Unit) {
        val account = try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(applicationContext)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (e: NoCurrentAccountSelectedException) {
            null
        }
        if (account == null) {
            Log.w(TAG, "No signed-in account - dropping")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block(account)
            } catch (e: Exception) {
                Log.e(TAG, "Push API call failed", e)
            }
        }
    }

    private fun showNotification(text: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, cannot show the notification")
            return
        }
        ensureChannel()
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Just opens the app for now, not the specific document - see the push
        // notifications plan doc's "remaining work" for deep-linking to a document.
        val contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NextSign")
            .setContentText(text.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Notifications", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }
}
