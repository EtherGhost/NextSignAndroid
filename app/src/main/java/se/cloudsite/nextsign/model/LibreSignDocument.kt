package se.cloudsite.nextsign.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.ui.theme.NextSignBlue
import se.cloudsite.nextsign.ui.theme.NextSignGreen
import se.cloudsite.nextsign.ui.theme.NextSignGrey

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

@Composable
fun statusLabel(fileStatus: Int, canSignNow: Boolean): String = when (fileStatus) {
    // fileStatus==1 means nobody has signed yet file-wide, so !canSignNow here can only
    // mean this account isn't a signer on the document at all (mySigner == null) - if it
    // were a signer, nobody-signed-yet would make canSignNow true. Reuses the same
    // "waiting on others" wording as the fileStatus==2 case below - same underlying
    // meaning, "no action expected from you here".
    1 -> if (canSignNow) {
        stringResource(R.string.status_ready_to_sign)
    } else {
        stringResource(R.string.status_waiting_on_others)
    }
    // Partially signed: still says so if it's this signer's turn, but once their own
    // part is done the doc is just waiting on the remaining signers, not on them.
    2 -> if (canSignNow) {
        stringResource(R.string.status_partially_signed)
    } else {
        stringResource(R.string.status_waiting_on_others)
    }
    3 -> stringResource(R.string.status_signed)
    else -> ""
}

// Blue: a signature is still owed by this signer. Green: nothing left for THIS signer
// to do, but they were genuinely part of it (fully signed, or their own part of a
// partially-signed doc). Grey: this signer isn't part of the document at all (only
// possible when fileStatus==1, see statusLabel) - distinct from green so it never reads
// as "you signed this" when you never had a part to play.
fun statusColor(fileStatus: Int, canSignNow: Boolean): Color = when {
    fileStatus == 1 && !canSignNow -> NextSignGrey
    fileStatus == 3 || (fileStatus == 2 && !canSignNow) -> NextSignGreen
    else -> NextSignBlue
}
