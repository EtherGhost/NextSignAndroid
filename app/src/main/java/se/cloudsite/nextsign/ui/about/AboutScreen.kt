package se.cloudsite.nextsign.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.BuildConfig
import se.cloudsite.nextsign.R

// Mirrors the Ubuntu Touch app's shared NextCommon.AboutPage layout (logo, name,
// version, description, divider, License, Disclaimer) rather than a plain wall of
// text - see NextSign/vendor/NextCommon/qml/NextCommon/AboutPage.qml for the source
// of truth this was ported from.
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // painterResource() can't load mipmap/ic_launcher directly - it's an
            // <adaptive-icon> XML (background + foreground layers), a format only the
            // launcher's own icon-loading path understands, not a plain
            // VectorDrawable/raster asset ("Only VectorDrawables and rasterized asset
            // types are supported", confirmed via the actual crash). Rebuilding the
            // same look (solid background color + the foreground vector) directly
            // instead, matching mipmap-anydpi-v26/ic_launcher.xml's own two layers.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF1F6FEB), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
            }

            Text("NextSign", style = MaterialTheme.typography.headlineMedium)

            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "A native Android client for LibreSign, the electronic signature app for " +
                    "Nextcloud. It does not prepare documents or place signature fields - " +
                    "that happens elsewhere. NextSign shows what is waiting for your " +
                    "signature and signs it with a tap using LibreSign's click-to-sign method.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("License", style = MaterialTheme.typography.titleMedium)
                Text("NextSign is licensed under the MIT License.")
                Text(
                    "Copyright (c) 2026 Etherghost",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Disclaimer", style = MaterialTheme.typography.titleMedium)
                // The "hobby project, no support" and "use at your own risk" framing
                // stays in the GitHub README only, not here - the About screen is for
                // the average paying user asking "does this work, who do I contact,"
                // not a support-model disclaimer. Only the trademark/affiliation
                // disclaimer belongs on-screen. Do not restore the removed lines here.
                Text(
                    "Not affiliated with, endorsed by, or supported by Nextcloud GmbH, " +
                        "the Nextcloud project, or the LibreSign project.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
