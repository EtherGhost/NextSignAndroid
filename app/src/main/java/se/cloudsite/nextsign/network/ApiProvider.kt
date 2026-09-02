package se.cloudsite.nextsign.network

import android.content.Context
import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.util.concurrent.ConcurrentHashMap
import retrofit2.NextcloudRetrofitApiBuilder

// Not private: DocumentDownloader needs this to build the signature-preview image
// path, which isn't declared on the LibreSignApi Retrofit interface (it's a binary
// download, not a Gson-deserializable OCS response).
const val LIBRESIGN_API_ENDPOINT = "/ocs/v2.php/apps/libresign/api/v1/"

// NextcloudAPI instances bind a service connection to the Nextcloud Files app and are
// meant to stay alive as long as possible rather than being recreated per call (see
// https://github.com/nextcloud/Android-SingleSignOn/issues/120#issuecomment-540069990),
// so they're cached per account here - same pattern as the reference Nextcloud Notes/
// Deck apps' own ApiProvider.
object ApiProvider {
    private val nextcloudApiCache = ConcurrentHashMap<String, NextcloudAPI>()
    private val libreSignApiCache = ConcurrentHashMap<String, LibreSignApi>()

    @Synchronized
    fun getLibreSignApi(context: Context, ssoAccount: SingleSignOnAccount): LibreSignApi {
        return libreSignApiCache.getOrPut(ssoAccount.name) {
            NextcloudRetrofitApiBuilder(getNextcloudApi(context, ssoAccount), LIBRESIGN_API_ENDPOINT)
                .create(LibreSignApi::class.java)
        }
    }

    // Not private: document downloading needs the raw NextcloudAPI directly (see
    // DocumentDownloader) rather than a Retrofit-wrapped interface, since a PDF
    // download isn't a Gson-deserializable OCS response.
    @Synchronized
    fun getNextcloudApi(context: Context, ssoAccount: SingleSignOnAccount): NextcloudAPI {
        return nextcloudApiCache.getOrPut(ssoAccount.name) {
            NextcloudAPI(context.applicationContext, ssoAccount, GsonBuilder().create()) { e ->
                invalidate(ssoAccount)
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun invalidate(ssoAccount: SingleSignOnAccount) {
        nextcloudApiCache.remove(ssoAccount.name)?.close()
        libreSignApiCache.remove(ssoAccount.name)
    }
}
