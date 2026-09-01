package se.cloudsite.nextsign.repository

import android.content.Context
import com.nextcloud.android.sso.model.SingleSignOnAccount
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.SignerStatus
import se.cloudsite.nextsign.model.VisibleElementRef
import se.cloudsite.nextsign.network.ApiProvider
import se.cloudsite.nextsign.network.RawLibreSignFile

sealed class LoadDocumentsResult {
    data class Success(val documents: List<LibreSignDocument>) : LoadDocumentsResult()
    data class Failure(val message: String) : LoadDocumentsResult()
}

// Turns the raw file/list response into the app's own domain model, mirroring the
// Ubuntu Touch app's LibreSignApiCore.js parseFileList() mapping (including its
// canSignNow logic - see LibreSignDocument.canSignNow for why it's decided per-signer,
// not from the file's own status field).
class LibreSignRepository(private val context: Context) {

    fun loadDocuments(account: SingleSignOnAccount): LoadDocumentsResult {
        return try {
            val api = ApiProvider.getLibreSignApi(context, account)
            val response = api.listFiles().execute()
            if (!response.isSuccessful) {
                return LoadDocumentsResult.Failure("LibreSign request failed with HTTP ${response.code()}.")
            }
            val rawFiles = response.body()?.ocs?.data?.data.orEmpty()
            LoadDocumentsResult.Success(rawFiles.mapNotNull { mapDocument(it) })
        } catch (e: Exception) {
            LoadDocumentsResult.Failure(e.message ?: e.toString())
        }
    }

    private fun mapDocument(raw: RawLibreSignFile): LibreSignDocument? {
        val uuid = raw.uuid ?: return null
        val mySigner = raw.signers?.firstOrNull { it.me == true }

        val signers = raw.signers.orEmpty().map { signer ->
            SignerStatus(
                displayName = signer.displayName.orEmpty(),
                signed = signer.signed.orEmpty(),
                me = signer.me == true
            )
        }

        val visibleElements = mySigner?.visibleElements.orEmpty().mapNotNull { element ->
            val elementId = element.elementId ?: return@mapNotNull null
            VisibleElementRef(elementId = elementId, type = element.type.orEmpty())
        }

        return LibreSignDocument(
            uuid = uuid,
            signUuid = mySigner?.signRequestUuid.orEmpty(),
            name = raw.name.orEmpty(),
            requestedBy = raw.requestedBy?.displayName.orEmpty(),
            createdAt = raw.createdAt.orEmpty(),
            signedAt = mySigner?.signed.orEmpty(),
            filePath = raw.files?.firstOrNull()?.file.orEmpty(),
            fileStatus = raw.status ?: -1,
            canSignNow = mySigner != null && mySigner.signed.isNullOrEmpty(),
            signers = signers,
            visibleElements = visibleElements
        )
    }
}
