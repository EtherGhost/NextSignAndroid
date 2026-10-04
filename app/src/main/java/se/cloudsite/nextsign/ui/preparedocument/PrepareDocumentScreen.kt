package se.cloudsite.nextsign.ui.preparedocument

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.PdfFieldPlacement
import se.cloudsite.nextsign.model.SignerCandidate

// Steps 1-4 of document preparation (see the feasibility doc in the suite's private
// handoff folder): confirm a PDF was received via Android's share sheet, ask which
// known account it's for when more than one is signed in, show a page-1 preview,
// let the user search for and pick signers (generic search, no identify-method
// filter), and tap the preview to place one signature field per signer, in the
// order they were added. No submission to LibreSign yet - that's a later step.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareDocumentScreen(
    documentName: String,
    knownAccountNames: List<String>,
    selectedAccountName: String?,
    previewBitmap: Bitmap?,
    previewLoading: Boolean,
    previewError: Boolean,
    signerSearchQuery: String,
    onSignerSearchQueryChange: (String) -> Unit,
    signerSearchResults: List<SignerCandidate>,
    signerSearchLoading: Boolean,
    signerSearchErrorMessage: String?,
    selectedSigners: List<SignerCandidate>,
    placedFields: Map<String, PdfFieldPlacement>,
    armedSignerIdentify: String?,
    onArmSigner: (String) -> Unit,
    fieldSizeFactor: Float,
    onFieldSizeFactorChange: (Float) -> Unit,
    onAddSigner: (SignerCandidate) -> Unit,
    onRemoveSigner: (SignerCandidate) -> Unit,
    onTapPlaceField: (xPt: Float, yPt: Float) -> Unit,
    onDragField: (identify: String, dxPt: Float, dyPt: Float) -> Unit,
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prepare_document_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_content_description))
                    }
                }
            )
        }
    ) { padding ->
        if (selectedAccountName == null && knownAccountNames.size > 1) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    stringResource(R.string.prepare_document_choose_account_prompt),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(knownAccountNames) { name ->
                        Text(
                            name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAccount(name) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } else {
            val armedSigner = selectedSigners.firstOrNull { it.identify == armedSignerIdentify }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.prepare_document_received, documentName.ifEmpty { stringResource(R.string.document_untitled) }),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    val accountName = selectedAccountName ?: knownAccountNames.firstOrNull()
                    if (accountName != null) {
                        Text(
                            stringResource(R.string.prepare_document_account_label, accountName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (previewLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
                    } else if (previewError) {
                        Text(
                            stringResource(R.string.prepare_document_preview_error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    } else if (previewBitmap != null) {
                        PdfPreviewWithPlacement(
                            bitmap = previewBitmap,
                            placedFields = placedFields,
                            signerNames = selectedSigners.associate { it.identify to it.displayName },
                            onTap = onTapPlaceField,
                            onDragField = onDragField,
                            onFieldTap = onArmSigner,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        if (armedSigner != null) {
                            Text(
                                stringResource(R.string.prepare_document_tap_to_place_hint, armedSigner.displayName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (placedFields.containsKey(armedSigner.identify)) {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        stringResource(R.string.signer_field_size_label, (fieldSizeFactor * 100).toInt()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = fieldSizeFactor,
                                        onValueChange = onFieldSizeFactorChange,
                                        valueRange = 0.5f..2f
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.signer_search_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }

                items(selectedSigners) { signer ->
                    val isArmed = signer.identify == armedSignerIdentify
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isArmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { onArmSigner(signer.identify) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(signer.displayName, style = MaterialTheme.typography.bodyLarge)
                            val detail = listOfNotNull(
                                signer.subname.takeIf { it.isNotEmpty() },
                                if (placedFields.containsKey(signer.identify)) stringResource(R.string.signer_field_placed_label) else null
                            ).joinToString(" · ")
                            if (detail.isNotEmpty()) {
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onRemoveSigner(signer) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.signer_remove_content_description))
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = signerSearchQuery,
                        onValueChange = onSignerSearchQueryChange,
                        label = { Text(stringResource(R.string.signer_search_label)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    if (signerSearchLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    } else if (signerSearchErrorMessage != null) {
                        Text(
                            signerSearchErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                items(signerSearchResults) { candidate ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddSigner(candidate) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(candidate.displayName, style = MaterialTheme.typography.bodyLarge)
                        if (candidate.subname.isNotEmpty()) {
                            Text(candidate.subname, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item {
                    val canSubmit = selectedSigners.isNotEmpty() &&
                        selectedSigners.all { placedFields.containsKey(it.identify) } &&
                        !isSubmitting
                    Button(
                        onClick = onSubmit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.prepare_document_submit_button))
                        }
                    }
                }
            }
        }
    }
}

// The rendered bitmap's pixel dimensions are the PDF page's own point dimensions
// (PdfRenderer renders 1 point = 1 pixel - see PdfPreviewRenderer), so converting a
// tap position to PDF points only needs the display-vs-bitmap scale factor, no DPI
// math. Field markers are drawn back from points to screen px using that same scale.
@Composable
private fun PdfPreviewWithPlacement(
    bitmap: Bitmap,
    placedFields: Map<String, PdfFieldPlacement>,
    signerNames: Map<String, String>,
    onTap: (xPt: Float, yPt: Float) -> Unit,
    onDragField: (identify: String, dxPt: Float, dyPt: Float) -> Unit,
    onFieldTap: (identify: String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val displayedWidthPx = with(density) { maxWidth.toPx() }
        val scale = bitmap.width.toFloat() / displayedWidthPx
        val displayedHeightDp = with(density) { (bitmap.height.toFloat() / scale).toDp() }
        // pointerInput only restarts its gesture-detection coroutine when its key
        // changes (here, scale) - it does NOT pick up a newly recomposed onTap lambda
        // on its own, so a stale closure would keep calling the PREVIOUS armed
        // signer's callback forever after the first tap. rememberUpdatedState keeps
        // the long-lived coroutine reading the latest callback without restarting it.
        val currentOnTap by rememberUpdatedState(onTap)

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(displayedHeightDp)
                .pointerInput(scale) {
                    detectTapGestures { offset ->
                        currentOnTap(offset.x * scale, offset.y * scale)
                    }
                },
            contentScale = ContentScale.FillBounds
        )

        placedFields.forEach { (identify, field) ->
            val leftDp = with(density) { (field.left / scale).toDp() }
            val topDp = with(density) { (field.top / scale).toDp() }
            val widthDp = with(density) { (field.width / scale).toDp() }
            val heightDp = with(density) { (field.height / scale).toDp() }
            val currentOnDrag by rememberUpdatedState(onDragField)
            val currentOnFieldTap by rememberUpdatedState(onFieldTap)
            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(widthDp, heightDp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                    .pointerInput(identify, scale) {
                        detectTapGestures { currentOnFieldTap(identify) }
                    }
                    .pointerInput(identify, scale) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            currentOnDrag(identify, dragAmount.x * scale, dragAmount.y * scale)
                        }
                    }
            ) {
                Text(
                    signerNames[identify] ?: identify,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}
