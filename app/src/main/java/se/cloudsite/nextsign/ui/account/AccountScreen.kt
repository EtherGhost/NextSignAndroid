package se.cloudsite.nextsign.ui.account

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.ui.common.AccountAvatar
import se.cloudsite.nextsign.ui.theme.NextSignBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    knownAccountNames: List<String>,
    currentAccountName: String,
    avatarsByName: Map<String, Bitmap?>,
    onSelectAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_switcher_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_content_description))
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            knownAccountNames.forEach { name ->
                val isCurrent = name == currentAccountName
                AccountRow(
                    name = name,
                    avatarBitmap = avatarsByName[name],
                    isCurrent = isCurrent,
                    onClick = { onSelectAccount(name) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AccountActionRow(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.account_switcher_add_account),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onAddAccount
            )
            AccountActionRow(
                icon = Icons.Filled.ExitToApp,
                label = stringResource(R.string.account_switcher_sign_out),
                tint = MaterialTheme.colorScheme.error,
                onClick = onSignOut
            )
        }
    }
}

@Composable
private fun AccountRow(name: String, avatarBitmap: Bitmap?, isCurrent: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .let { if (isCurrent) it.border(1.dp, NextSignBlue, shape) else it }
            .background(
                if (isCurrent) NextSignBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow,
                shape
            )
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(12.dp)
    ) {
        AccountAvatar(
            bitmap = avatarBitmap,
            initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (isCurrent) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.account_switcher_current),
                tint = NextSignBlue
            )
        }
    }
}

@Composable
private fun AccountActionRow(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}
