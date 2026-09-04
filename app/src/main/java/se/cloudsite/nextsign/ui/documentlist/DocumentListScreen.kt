package se.cloudsite.nextsign.ui.documentlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.model.LibreSignDocument
import se.cloudsite.nextsign.model.statusLabel
import se.cloudsite.nextsign.ui.common.StatusPill

enum class SortMode { DATE_DESC, DATE_ASC, NAME_ASC }

fun sortDocuments(documents: List<LibreSignDocument>, sortMode: SortMode): List<LibreSignDocument> {
    return when (sortMode) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Status badge on its own row, always left-aligned at the same position -
            // keeping it inline next to the name put it at a different horizontal spot
            // on every card depending on how long the name was.
            Text(
                text = document.name.ifEmpty { stringResource(R.string.document_untitled) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (document.requestedBy.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.document_requested_by, document.requestedBy),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            StatusBadge(fileStatus = document.fileStatus)
        }
    }
}

@Composable
private fun StatusBadge(fileStatus: Int) {
    // Matches the Ubuntu Touch app's own status-badge colors (HomePage.qml).
    val color = if (fileStatus == 3) Color(0xFF5A8F3C) else Color(0xFFB37A2A)
    StatusPill(text = statusLabel(fileStatus), color = color, modifier = Modifier.padding(top = 6.dp))
}
