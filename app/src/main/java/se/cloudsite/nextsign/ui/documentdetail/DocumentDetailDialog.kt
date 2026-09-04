package se.cloudsite.nextsign.ui.documentdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.statusLabel
import se.cloudsite.nextsign.ui.common.StatusPill

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
                Text(
                    document.name.ifEmpty { stringResource(R.string.document_untitled) },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(statusLabel(document.fileStatus))

                document.signers.forEach { signer ->
                    val hasSigned = signer.signed.isNotEmpty()
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(signer.displayName.ifEmpty { stringResource(R.string.document_unknown_signer) })
                            StatusPill(
                                text = if (hasSigned) stringResource(R.string.status_signed) else stringResource(R.string.status_ready_to_sign),
                                color = if (hasSigned) Color(0xFF5A8F3C) else Color(0xFFB37A2A)
                            )
                        }
                    }
                }

                if (document.messageForMe.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                stringResource(R.string.document_message_from_requester),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(document.messageForMe, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (document.canSignNow) {
                    Button(
                        onClick = onSignClick,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(if (signing) R.string.signing_in_progress else R.string.sign_document_button))
                    }
                }

                OutlinedButton(
                    onClick = onValidateClick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (validating) R.string.validating_in_progress else R.string.validation_info_button))
                }

                OutlinedButton(
                    onClick = onOpenFileClick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (downloading) R.string.opening_in_progress else R.string.open_file_button))
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.close_button))
                }
            }
        }
    }
}
