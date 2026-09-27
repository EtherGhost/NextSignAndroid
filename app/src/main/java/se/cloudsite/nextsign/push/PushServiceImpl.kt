package se.cloudsite.nextsign.push

import android.util.Log
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import se.cloudsite.nextsign.network.ApiProvider
import se.cloudsite.nextsign.util.NotificationMode
import se.cloudsite.nextsign.util.PushPreference

private const val TAG = "NextSignPush"
private const val NOTIFICATION_ID = 9001

// Real-time push (Tier 2 of the push notifications plan) - receives real Nextcloud
// WebPush notifications directly via whichever distributor the user has installed
// (e.g. ntfy). No bridge/server of our own: Nextcloud sends encrypted pushes straight
// to the distributor's own public endpoint, and this library decrypts them on-device
// before onMessage() is called. Confirmed against Nextcloud's own WebPushController.php
// source and Nextcloud Talk's real Android client, not guessed.
//
// Per-account: each known account registers under its own UnifiedPush `instance`
// string (its account name) instead of one shared `"default"` - see
// MainActivity.syncPushRegistration()/disableInstantPush(). That instance string is
// handed back into every callback here, so it doubles as the account identifier with
// no separate persisted endpoint-to-account mapping needed - `instance` *is* the
// account name.
class PushServiceImpl : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val pubKeySet = endpoint.pubKeySet
        if (pubKeySet == null) {
            Log.w(TAG, "onNewEndpoint: no web push key set on the endpoint, cannot register")
            return
        }
        withAccount(instance) { account ->
            val api = ApiProvider.getNotificationsApi(applicationContext, account)
            val response = api.registerWebPush(endpoint.url, pubKeySet.pubKey, pubKeySet.auth, "all").execute()
            Log.i(TAG, "registerWebPush: HTTP ${response.code()} for ${account.name}")
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
            withAccount(instance) { account ->
                val api = ApiProvider.getNotificationsApi(applicationContext, account)
                val response = api.activateWebPush(activationToken).execute()
                Log.i(TAG, "activateWebPush: HTTP ${response.code()} for ${account.name}")
            }
            return
        }
        Log.i(TAG, "onMessage ($instance): $content")
        if (PushPreference.getMode(applicationContext, instance) != NotificationMode.INSTANT) {
            // Can still receive a stray message right after this account's mode
            // changed away from Instant, before unregisterWebPush() has taken effect
            // server-side - drop it rather than show a notification for a mode this
            // account just left.
            Log.i(TAG, "$instance is not in Instant notification mode - dropping")
            return
        }
        // Nextcloud's push payload is deliberately minimal (Push.php's encodeNotif(),
        // capped near 240 bytes pre-encryption): {"nid","app","subject","type","id"} -
        // no separate message/body field, and "id" is LibreSign's own internal numeric
        // SignRequest id (confirmed from NotificationListener.php's setObject('signRequest',
        // ...)), not the document uuid/signUuid this app tracks anywhere else - so this
        // can't deep-link to the specific document, only show the subject text and open
        // the app (which refreshes via onNewIntent(), see MainActivity).
        val subject = json?.optString("subject", "")?.ifEmpty { null }
        LocalNotifier.show(applicationContext, subject ?: content, NOTIFICATION_ID)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "UnifiedPush registration failed for $instance: $reason")
    }

    override fun onUnregistered(instance: String) {
        Log.i(TAG, "UnifiedPush unregistered for $instance")
    }

    private fun withAccount(accountName: String, block: (SingleSignOnAccount) -> Unit) {
        val account = try {
            AccountImporter.getSingleSignOnAccount(applicationContext, accountName)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        }
        if (account == null) {
            Log.w(TAG, "Account $accountName no longer available - dropping")
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
}
