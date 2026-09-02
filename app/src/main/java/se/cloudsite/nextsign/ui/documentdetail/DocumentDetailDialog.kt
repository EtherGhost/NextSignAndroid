package se.cloudsite.nextsign.ui.documentdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.statusLabel

// A custom Dialog rather than AlertDialog's fixed confirm/dismiss button pair - this
// needs up to four actions (Sign, Validation info, Open file, Close), mirroring the
// Ubuntu Touch app's own detail popup's stacked-button layout.
@Composable
fun DocumentDetailDialog(
    document: LibreSignDocument,
    signing: Boolean,
    validating: Boolean,
    downloading: Boolean,
    onSignClick: () -> Unit,
    onValidateClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = signing || validating || downloading

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(document.name.ifEmpty { "Untitled document" }, style = MaterialTheme.typography.titleLarge)
                Text(statusLabel(document.fileStatus))

                document.signers.forEach { signer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(signer.displayName.ifEmpty { "Unknown signer" })
                        val hasSigned = signer.signed.isNotEmpty()
                        Text(
                            text = if (hasSigned) "Signed" else "Ready to sign",
                            color = if (hasSigned) Color(0xFF5A8F3C) else Color(0xFFB37A2A)
                        )
                    }
                }

                if (document.canSignNow) {
                    Button(
                        onClick = onSignClick,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (signing) "Signing..." else "Sign document")
                    }
                }

                OutlinedButton(
                    onClick = onValidateClick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (validating) "Validating..." else "Validation info")
                }

                OutlinedButton(
                    onClick = onOpenFileClick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (downloading) "Opening..." else "Open file")
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }
}
