package se.cloudsite.nextsign.model

data class LibreSignDocument(
    val uuid: String,
    // sign/uuid/{signUuid} - empty if this account isn't a signer on this document.
    val signUuid: String,
    val name: String,
    val requestedBy: String,
    val createdAt: String,
    val signedAt: String,
    val filePath: String,
    // Raw file-level status (0=draft, 1=ready to sign, 2=partially signed, 3=fully
    // signed, 4=deleted) - only shown, never trusted alone to decide canSignNow.
    val fileStatus: Int,
    // Whether THIS signer still needs to act - decided per-signer (mySigner exists and
    // hasn't signed yet), not from fileStatus. A multi-signer document's fileStatus
    // moves to 2 ("partially signed") the moment ANY signer finishes, not just this
    // one - confirmed live against a real server in the Ubuntu Touch app - so a signer
    // who hadn't signed yet went missing from a status-only filtered list.
    val canSignNow: Boolean,
    val signers: List<SignerStatus>,
    val visibleElements: List<VisibleElementRef>,
    // The requester's optional note for the current signer specifically - empty if
    // none was set, or if this account isn't a signer on this document.
    val messageForMe: String
)

data class SignerStatus(
    val displayName: String,
    val signed: String,
    val me: Boolean
)

data class VisibleElementRef(
    val elementId: Int,
    val type: String
)

fun statusLabel(fileStatus: Int): String = when (fileStatus) {
    1 -> "Ready to sign"
    2 -> "Partially signed"
    3 -> "Signed"
    else -> ""
}
