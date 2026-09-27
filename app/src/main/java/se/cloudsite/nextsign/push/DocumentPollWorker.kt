package se.cloudsite.nextsign.push

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.repository.LibreSignRepository
import se.cloudsite.nextsign.repository.LoadDocumentsResult
import se.cloudsite.nextsign.util.AccountHistory
import se.cloudsite.nextsign.util.NotificationMode
import se.cloudsite.nextsign.util.PushPreference
import se.cloudsite.nextsign.util.SeenDocumentsStore

private const val TAG = "NextSignPoll"
private const val UNIQUE_WORK_NAME = "document_poll"

// Tier 1 of the push notifications plan - a periodic-sync fallback that needs no
// server, no distributor app, and no push registration at all, so it works
// identically regardless of whether real-time push (Tier 2, PushServiceImpl) is
// available or working. 15 minutes is WorkManager's minimum period for periodic
// work, not a deliberately chosen cadence - Android may delay it further under Doze.
// Both tiers can run at once without duplicate notifications: SeenDocumentsStore is
// updated by MainActivity's own foreground refresh() too, so anything the user
// already saw (via push or just opening the app) is never re-notified by a poll.
//
// Per-account, covering every known account (AccountHistory), not just whichever one
// happens to be open in the foreground right now - each account's own notification
// mode decides whether it gets polled at all. AccountImporter.getSingleSignOnAccount()
// (not SingleAccountHelper.getCurrentSingleSignOnAccount()) fetches a specific named
// account's token without touching the SSO library's persisted "current account"
// pointer, so this never disturbs whichever account the foreground UI has open.
class DocumentPollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accountNames = AccountHistory.list(applicationContext)
        var needsRetry = false

        for (accountName in accountNames) {
            // Runs in both BACKGROUND_ONLY and INSTANT modes for this account - in
            // INSTANT it's a backup to real-time push, not redundant with it
            // (SeenDocumentsStore is what prevents a duplicate notification either way).
            if (PushPreference.getMode(applicationContext, accountName) == NotificationMode.OFF) {
                continue
            }

            val account = try {
                AccountImporter.getSingleSignOnAccount(applicationContext, accountName)
            } catch (e: NextcloudFilesAppAccountNotFoundException) {
                Log.w(TAG, "Account $accountName no longer available - skipping")
                continue
            }

            if (!pollAccount(account)) {
                needsRetry = true
            }
        }

        return if (needsRetry) Result.retry() else Result.success()
    }

    // Returns false if this account's poll failed and should be retried.
    private suspend fun pollAccount(account: SingleSignOnAccount): Boolean {
        val repository = LibreSignRepository(applicationContext)
        val result = withContext(Dispatchers.IO) { repository.loadDocuments(account) }
        val documents = when (result) {
            is LoadDocumentsResult.Success -> result.documents
            is LoadDocumentsResult.Failure -> {
                Log.w(TAG, "loadDocuments failed for ${account.name}: ${result.message}")
                return false
            }
        }

        val wasInitialized = SeenDocumentsStore.isInitialized(applicationContext, account.name)
        val previouslySeen = SeenDocumentsStore.getSeen(applicationContext, account.name)

        if (wasInitialized) {
            val newlyNeedsSignature = documents.filter { it.uuid !in previouslySeen && it.canSignNow }
            newlyNeedsSignature.forEach { document -> notifyNewDocument(document) }
        }

        SeenDocumentsStore.markSeen(applicationContext, account.name, documents.map { it.uuid }.toSet())
        PendingSignatureBadges.sync(applicationContext, account.name, documents)
        return true
    }

    private fun notifyNewDocument(document: LibreSignDocument) {
        val name = document.name.ifEmpty { applicationContext.getString(R.string.document_untitled) }
        val text = applicationContext.getString(R.string.poll_new_document_notification, name)
        LocalNotifier.show(applicationContext, text, document.uuid.hashCode())
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DocumentPollWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
