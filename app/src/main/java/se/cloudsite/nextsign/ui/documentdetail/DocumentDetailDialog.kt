package se.cloudsite.nextsign.ui.documentdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.statusLabel

// Read-only for now (per-signer status only) - Sign/Validate actions land in the next
// porting step, matching the plan's staged sequence.
@Composable
fun DocumentDetailDialog(document: LibreSignDocument, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.name.ifEmpty { "Untitled document" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
