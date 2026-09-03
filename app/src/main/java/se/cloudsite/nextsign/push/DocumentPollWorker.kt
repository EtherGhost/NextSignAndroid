package se.cloudsite.nextsign.push

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.repository.LibreSignRepository
import se.cloudsite.nextsign.repository.LoadDocumentsResult
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
class DocumentPollWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Runs in both BACKGROUND_ONLY and INSTANT modes - in INSTANT it's a backup
        // to real-time push, not redundant with it (SeenDocumentsStore is what
        // prevents a duplicate notification either way).
        if (PushPreference.getMode(applicationContext) == NotificationMode.OFF) {
            return Result.success()
        }

        val account = try {
            SingleAccountHelper.getCurrentSingleSignOnAccount(applicationContext)
        } catch (e: NextcloudFilesAppAccountNotFoundException) {
            null
        } catch (e: NoCurrentAccountSelectedException) {
            null
        } ?: return Result.success()

        val repository = LibreSignRepository(applicationContext)
        val result = withContext(Dispatchers.IO) { repository.loadDocuments(account) }
        val documents = when (result) {
            is LoadDocumentsResult.Success -> result.documents
            is LoadDocumentsResult.Failure -> {
                Log.w(TAG, "loadDocuments failed: ${result.message}")
                return Result.retry()
            }
        }

        val wasInitialized = SeenDocumentsStore.isInitialized(applicationContext)
        val previouslySeen = SeenDocumentsStore.getSeen(applicationContext)

        if (wasInitialized) {
            val newlyNeedsSignature = documents.filter { it.uuid !in previouslySeen && it.canSignNow }
            newlyNeedsSignature.forEach { document -> notifyNewDocument(document) }
        }

        SeenDocumentsStore.markSeen(applicationContext, documents.map { it.uuid }.toSet())
        return Result.success()
    }

    private fun notifyNewDocument(document: LibreSignDocument) {
        val name = document.name.ifEmpty { "Untitled document" }
        LocalNotifier.show(applicationContext, "\"$name\" is ready to sign", document.uuid.hashCode())
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
