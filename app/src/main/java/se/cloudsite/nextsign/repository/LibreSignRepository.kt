package se.cloudsite.nextsign.repository

import android.content.Context
import androidx.annotation.StringRes
import com.nextcloud.android.sso.model.SingleSignOnAccount
import retrofit2.Response
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.SignatureElement
import se.cloudsite.nextsign.model.SignerStatus
import se.cloudsite.nextsign.model.SignerVerdict
import se.cloudsite.nextsign.model.ValidationSummary
import se.cloudsite.nextsign.model.VisibleElementRef
import se.cloudsite.nextsign.network.ApiProvider
import se.cloudsite.nextsign.network.RawLibreSignFile
import se.cloudsite.nextsign.network.RawSignatureElement
import se.cloudsite.nextsign.network.SignElementBody
import se.cloudsite.nextsign.network.SignRequestBody
import se.cloudsite.nextsign.network.SignatureElementCreateRequest
import se.cloudsite.nextsign.network.SignatureElementEntry
import se.cloudsite.nextsign.network.SignatureElementFileBody
import se.cloudsite.nextsign.network.SignatureElementUpdateRequest

sealed class LoadDocumentsResult {
    data class Success(val documents: List<LibreSignDocument>) : LoadDocumentsResult()
    data class Failure(val message: String) : LoadDocumentsResult()
}

sealed class SignResult {
    object Success : SignResult()
    data class Failure(val message: String) : SignResult()
}

sealed class ValidateResult {
    data class Success(val summary: ValidationSummary) : ValidateResult()
    data class Failure(val message: String) : ValidateResult()
}

sealed class SignatureElementsResult {
    data class Success(val elements: List<SignatureElement>) : SignatureElementsResult()
    data class Failure(val message: String) : SignatureElementsResult()
}

sealed class SaveSignatureElementResult {
    data class Success(val elements: List<SignatureElement>) : SaveSignatureElementResult()
    data class Failure(val message: String) : SaveSignatureElementResult()
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
                return LoadDocumentsResult.Failure(errorMessage(response, R.string.action_libresign_request))
            }
            val rawFiles = response.body()?.ocs?.data?.data.orEmpty()
            LoadDocumentsResult.Success(rawFiles.mapNotNull { mapDocument(it) })
        } catch (e: Exception) {
            LoadDocumentsResult.Failure(e.message ?: e.toString())
        }
    }

    // profileNodeIdsByType: { "signature": nodeId, "initial": nodeId, ... } - the
    // signer's own registered signature/initials images, from loadSignatureElements().
    // A placeholder without a registered image of the same type is left out entirely -
    // the server rejects the whole sign request if it gets a documentElementId with no
    // profileNodeId at all. Matches the Ubuntu Touch app's own element-building logic.
    fun signDocument(
        account: SingleSignOnAccount,
        signUuid: String,
        visibleElements: List<VisibleElementRef>,
        profileNodeIdsByType: Map<String, Int>
    ): SignResult {
        return try {
            val api = ApiProvider.getLibreSignApi(context, account)
            val elements = visibleElements.mapNotNull { element ->
                val nodeId = profileNodeIdsByType[element.type] ?: return@mapNotNull null
                SignElementBody(documentElementId = element.elementId, profileNodeId = nodeId)
            }
            val response = api.signDocument(signUuid, SignRequestBody(elements = elements)).execute()
            if (!response.isSuccessful) {
                return SignResult.Failure(errorMessage(response, R.string.action_signing))
            }
            SignResult.Success
        } catch (e: Exception) {
            SignResult.Failure(e.message ?: e.toString())
        }
    }

    fun validateDocument(account: SingleSignOnAccount, uuid: String): ValidateResult {
        return try {
            val api = ApiProvider.getLibreSignApi(context, account)
            val response = api.validateFile(uuid).execute()
            if (!response.isSuccessful) {
                return ValidateResult.Failure(errorMessage(response, R.string.action_validation))
            }
            val data = response.body()?.ocs?.data
            if (data == null) {
                return ValidateResult.Failure(context.getString(R.string.libresign_unexpected_response))
            }
            ValidateResult.Success(
                ValidationSummary(
                    statusText = data.statusText.orEmpty(),
                    signers = data.signers.orEmpty().map { signer ->
                        SignerVerdict(
                            displayName = signer.displayName.orEmpty(),
                            signed = signer.signed.orEmpty(),
                            signatureLabel = signer.signature_validation?.label.orEmpty(),
                            certificateLabel = signer.certificate_validation?.label.orEmpty()
                        )
                    }
                )
            )
        } catch (e: Exception) {
            ValidateResult.Failure(e.message ?: e.toString())
        }
    }

    fun loadSignatureElements(account: SingleSignOnAccount): SignatureElementsResult {
        return try {
            val api = ApiProvider.getLibreSignApi(context, account)
            val response = api.getSignatureElements().execute()
            if (!response.isSuccessful) {
                return SignatureElementsResult.Failure(errorMessage(response, R.string.action_libresign_request))
            }
            val rawElements = response.body()?.ocs?.data?.elements.orEmpty()
            SignatureElementsResult.Success(rawElements.mapNotNull { mapSignatureElement(it) })
        } catch (e: Exception) {
            SignatureElementsResult.Failure(e.message ?: e.toString())
        }
    }

    // Replaces an already-registered element's image in place (existingNodeId != null)
    // rather than creating another one of the same type - otherwise every pick/draw
    // just piles up a new duplicate element server-side, none of them marked as the
    // account's actual signature. Confirmed live as a real bug in the Ubuntu Touch app.
    fun saveSignatureElement(
        account: SingleSignOnAccount,
        elementType: String,
        base64DataUri: String,
        existingNodeId: Int?
    ): SaveSignatureElementResult {
        return try {
            val api = ApiProvider.getLibreSignApi(context, account)
            val response = if (existingNodeId != null) {
                api.updateSignatureElement(
                    existingNodeId,
                    SignatureElementUpdateRequest(type = elementType, file = SignatureElementFileBody(base64DataUri))
                ).execute()
            } else {
                api.createSignatureElement(
                    SignatureElementCreateRequest(
                        elements = listOf(SignatureElementEntry(type = elementType, file = SignatureElementFileBody(base64DataUri)))
                    )
                ).execute()
            }
            if (!response.isSuccessful) {
                return SaveSignatureElementResult.Failure(errorMessage(response, R.string.action_saving_signature))
            }
            val rawElements = response.body()?.ocs?.data?.elements.orEmpty()
            SaveSignatureElementResult.Success(rawElements.mapNotNull { mapSignatureElement(it) })
        } catch (e: Exception) {
            SaveSignatureElementResult.Failure(e.message ?: e.toString())
        }
    }

    private fun mapSignatureElement(raw: RawSignatureElement): SignatureElement? {
        val nodeId = raw.file?.nodeId ?: return null
        return SignatureElement(type = raw.type.orEmpty(), nodeId = nodeId, starred = raw.starred == true)
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
            visibleElements = visibleElements,
            messageForMe = mySigner?.description.orEmpty()
        )
    }

    // The SSO bridge does NOT preserve the real server's OCS error JSON on a non-2xx
    // response - it detects HTTP failures inside the Nextcloud Files app process,
    // serializes only a generic "request failed with status code N" exception across
    // the AIDL boundary, and Retrofit2Helper.convertExceptionToResponse() builds the
    // error body from that exception's toString(), not from the server's actual
    // response text (confirmed by reading the library's 1.3.4 source). So unlike the
    // Ubuntu Touch app, which can parse a real "message"/"errors" field out of a failed
    // response body, this app can only reliably report the HTTP status code - the
    // errorBody() text is included as a best-effort detail, but it's the generic
    // exception message, not LibreSign's own validation message.
    private fun <T> errorMessage(response: Response<T>, @StringRes actionRes: Int): String {
        val detail = response.errorBody()?.string().orEmpty()
        val base = context.getString(R.string.error_action_failed_http, context.getString(actionRes), response.code())
        return if (detail.isNotBlank()) "$base\n\n$detail" else base
    }
}
