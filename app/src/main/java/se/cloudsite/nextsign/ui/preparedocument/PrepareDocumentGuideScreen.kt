package se.cloudsite.nextsign.ui.preparedocument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.R

// There's no in-app document/file picker - preparing a document only starts by
// sharing a PDF to NextSign from the Nextcloud app itself (see PrepareDocumentScreen
// and the feasibility doc). This screen exists purely so that entry point is
// discoverable from the drawer at all, rather than only by accident - it doesn't
// start a flow itself, just explains the real one and jumps to Nextcloud.
//
// Signing into NextSign at all already requires the Nextcloud app to be
// installed, but it can be uninstalled afterwards while still signed in (every
// other screen would already be broken too in that case, not just this one) -
// so the install fallback is still needed here, not dead code.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareDocumentGuideScreen(
    nextcloudInstalled: Boolean,
    onOpenNextcloud: () -> Unit,
    onInstallNextcloud: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prepare_document_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_content_description))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.prepare_document_guide_message), style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = if (nextcloudInstalled) onOpenNextcloud else onInstallNextcloud,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (nextcloudInstalled) R.string.prepare_document_guide_open_nextcloud_button else R.string.install_nextcloud_button
                    )
                )
            }
        }
    }
}
