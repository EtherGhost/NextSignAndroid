package se.cloudsite.nextsign.ui.documentlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.statusColor
import se.cloudsite.nextsign.model.statusLabel

enum class SortMode { NEEDS_SIGNATURE_FIRST, DATE_DESC, DATE_ASC, NAME_ASC }

fun sortDocuments(documents: List<LibreSignDocument>, sortMode: SortMode): List<LibreSignDocument> {
    return when (sortMode) {
        // Groups by canSignNow (true first), newest-first within each group - answers
        // "what needs my attention" without a separate filter UI. The app's default.
        SortMode.NEEDS_SIGNATURE_FIRST -> documents.sortedWith(
            compareByDescending<LibreSignDocument> { it.canSignNow }.thenByDescending { parseCreatedAt(it.createdAt) }
        )
        SortMode.NAME_ASC -> documents.sortedBy { it.name.lowercase() }
        SortMode.DATE_ASC -> documents.sortedBy { parseCreatedAt(it.createdAt) }
        SortMode.DATE_DESC -> documents.sortedByDescending { parseCreatedAt(it.createdAt) }
    }
}

private fun parseCreatedAt(value: String): Instant = try {
    OffsetDateTime.parse(value).toInstant()
} catch (e: Exception) {
    Instant.EPOCH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    documents: List<LibreSignDocument>,
    loading: Boolean,
    errorMessage: String,
    onRefresh: () -> Unit,
    onDocumentClick: (LibreSignDocument) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (!loading && documents.isEmpty() && errorMessage.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.document_list_empty))
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(documents, key = { it.uuid }) { document ->
                    DocumentRow(document = document, onClick = { onDocumentClick(document) })
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: LibreSignDocument, onClick: () -> Unit) {
    val color = statusColor(document.fileStatus, document.canSignNow)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = document.name.ifEmpty { stringResource(R.string.document_untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (document.requestedBy.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.document_requested_by, document.requestedBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel(document.fileStatus, document.canSignNow),
                    color = color,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.document_row_menu_content_description)
            )
        }
    }
}
