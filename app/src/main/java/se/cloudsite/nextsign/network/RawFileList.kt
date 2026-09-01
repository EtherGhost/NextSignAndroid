package se.cloudsite.nextsign.network

import com.google.gson.annotations.SerializedName

// Raw wire shapes for GET file/list?details=true, matching exactly what the Ubuntu
// Touch app's LibreSignApiCore.js parseFileList() already reverse-engineered against a
// real server. Kept separate from the app's own domain model (model/) so a server-shape
// quirk doesn't leak into the UI layer - see LibreSignRepository for the mapping.
data class FileListData(
    val data: List<RawLibreSignFile>?
)

data class RawLibreSignFile(
    val uuid: String?,
    val name: String?,
    // 0=draft, 1=ready to sign, 2=partially signed, 3=fully signed, 4=deleted.
    val status: Int?,
    @SerializedName("requested_by") val requestedBy: RawRequestedBy?,
    @SerializedName("created_at") val createdAt: String?,
    val files: List<RawFileEntry>?,
    val signers: List<RawSigner>?
)

data class RawRequestedBy(
    val displayName: String?
)

data class RawFileEntry(
    val file: String?
)

data class RawSigner(
    val me: Boolean?,
    val displayName: String?,
    // sign/uuid/{uuid} needs THIS, not the file's own uuid, despite LibreSign's own API
    // docs describing both this and file/validate/uuid identically - confirmed live
    // against a real instance in the Ubuntu Touch app.
    @SerializedName("sign_request_uuid") val signRequestUuid: String?,
    val signed: String?,
    val visibleElements: List<RawVisibleElement>?
)

data class RawVisibleElement(
    val elementId: Int?,
    val type: String?
)
