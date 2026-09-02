package se.cloudsite.nextsign.ui.documentdetail

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun SignConfirmDialog(documentName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign this document?") },
        text = {
            Text(
                "This will sign \"${documentName.ifEmpty { "Untitled document" }}\" using " +
                    "LibreSign's click-to-sign method. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
