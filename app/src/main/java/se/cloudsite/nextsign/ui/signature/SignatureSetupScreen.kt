package se.cloudsite.nextsign.ui.signature

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureSetupScreen(
    hasSignature: Boolean,
    previewBitmap: Bitmap?,
    loadingPreview: Boolean,
    saving: Boolean,
    errorMessage: String,
    onPickImage: () -> Unit,
    onDrawSignature: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signature") },
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
            Text(
                if (hasSignature) {
                    "You have a signature image set up. Documents with a visible signature field will use it when you sign."
                } else {
                    "Pick an image of your signature, or draw one. Documents with a visible signature field will use it when you sign."
                }
            )

            // A signature image is transparent-background ink - without an opaque
            // backdrop it's nearly invisible against a dark theme, same reasoning as
            // the Ubuntu Touch app's own preview card.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.White
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        previewBitmap != null -> Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Signature preview",
                            modifier = Modifier.padding(16.dp)
                        )
                        loadingPreview -> CircularProgressIndicator()
                    }
                }
            }

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = onDrawSignature, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Saving..." else "Draw signature")
            }
            OutlinedButton(onClick = onPickImage, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Saving..." else "Pick an image")
            }
        }
    }
}
