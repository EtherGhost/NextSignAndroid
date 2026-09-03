package se.cloudsite.nextsign.ui.documentdetail

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import se.cloudsite.nextsign.R

@Composable
fun SignConfirmDialog(documentName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.sign_confirm_message,
                    documentName.ifEmpty { stringResource(R.string.document_untitled) }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.sign_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}
