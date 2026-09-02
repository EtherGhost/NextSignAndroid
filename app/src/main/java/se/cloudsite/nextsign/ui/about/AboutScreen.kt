package se.cloudsite.nextsign.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.BuildConfig

// No license section yet - the user hasn't decided on one for this app. Do not add a
// license here without being told what it is; the Ubuntu Touch app's MIT choice is not
// necessarily what this app will use.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("NextSign", style = MaterialTheme.typography.headlineMedium)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)

            Text(
                "A native Android client for LibreSign, the electronic signature app for " +
                    "Nextcloud. It does not prepare documents or place signature fields - " +
                    "that happens elsewhere. NextSign shows what is waiting for your " +
                    "signature and signs it with a tap using LibreSign's click-to-sign method."
            )

            Text(
                "This is a hobby project, built and maintained in spare time - not an " +
                    "official or supported product. Not affiliated with, endorsed by, or " +
                    "supported by Nextcloud GmbH, the Nextcloud project, or the LibreSign " +
                    "project."
            )

            Text(
                "Use it at your own risk, especially anything involving actually signing " +
                    "a document. Verify independently that a signature was applied " +
                    "correctly before relying on it for anything that matters."
            )
        }
    }
}
