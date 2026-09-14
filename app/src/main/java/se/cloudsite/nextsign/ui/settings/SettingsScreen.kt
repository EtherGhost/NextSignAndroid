package se.cloudsite.nextsign.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.util.NotificationMode
import se.cloudsite.nextsign.util.ThemeMode

// Native autonyms (each language's own name for itself), not string resources - a
// language picker has to stay legible to someone who can't currently read the active
// UI language, so these are deliberately the same in every locale rather than
// translated. Order matches the language's own ISO 639-1 code, no particular ranking.
// Keep in sync with the values-<tag>/strings.xml resource directories that exist.
data class SupportedLanguage(val tag: String, val nativeName: String)

val SUPPORTED_LANGUAGES = listOf(
    SupportedLanguage("en", "English"),
    SupportedLanguage("da", "Dansk"),
    SupportedLanguage("de", "Deutsch"),
    SupportedLanguage("es", "Español"),
    SupportedLanguage("fr", "Français"),
    SupportedLanguage("it", "Italiano"),
    SupportedLanguage("nl", "Nederlands"),
    SupportedLanguage("nb", "Norsk bokmål"),
    SupportedLanguage("pl", "Polski"),
    SupportedLanguage("ru", "Русский"),
    SupportedLanguage("sv", "Svenska")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    languageTag: String?,
    onLanguageSelected: (String?) -> Unit,
    notificationMode: NotificationMode,
    onNotificationModeSelected: (NotificationMode) -> Unit,
    hasPushDistributor: Boolean,
    onInstallPushHelper: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)

            SettingsRadioOption(
                label = stringResource(R.string.theme_follow_system),
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { onThemeModeSelected(ThemeMode.SYSTEM) }
            )
            SettingsRadioOption(
                label = stringResource(R.string.theme_light),
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { onThemeModeSelected(ThemeMode.LIGHT) }
            )
            SettingsRadioOption(
                label = stringResource(R.string.theme_dark),
                selected = themeMode == ThemeMode.DARK,
                onClick = { onThemeModeSelected(ThemeMode.DARK) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)

            SettingsRadioOption(
                label = stringResource(R.string.language_system_default),
                selected = languageTag == null,
                onClick = { onLanguageSelected(null) }
            )
            SUPPORTED_LANGUAGES.forEach { language ->
                SettingsRadioOption(
                    label = language.nativeName,
                    selected = languageTag == language.tag,
                    onClick = { onLanguageSelected(language.tag) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)

            NotificationOption(
                label = stringResource(R.string.notification_off_label),
                description = stringResource(R.string.notification_off_description),
                selected = notificationMode == NotificationMode.OFF,
                onClick = { onNotificationModeSelected(NotificationMode.OFF) }
            )
            NotificationOption(
                label = stringResource(R.string.notification_background_label),
                description = stringResource(R.string.notification_background_description),
                selected = notificationMode == NotificationMode.BACKGROUND_ONLY,
                onClick = { onNotificationModeSelected(NotificationMode.BACKGROUND_ONLY) }
            )
            NotificationOption(
                label = stringResource(R.string.notification_instant_label),
                description = stringResource(R.string.notification_instant_description),
                selected = notificationMode == NotificationMode.INSTANT,
                onClick = { onNotificationModeSelected(NotificationMode.INSTANT) }
            )

            if (notificationMode == NotificationMode.INSTANT) {
                if (hasPushDistributor) {
                    Text(
                        stringResource(R.string.notification_instant_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                    )
                } else {
                    // Instant notifications need a small "push helper" app installed
                    // (a UnifiedPush distributor) - without one, only the background
                    // check actually runs even though Instant is selected. This
                    // guidance is what makes that requirement discoverable, rather
                    // than notifications just silently arriving late with no
                    // explanation.
                    Text(
                        stringResource(R.string.notification_instant_guidance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                    )
                    OutlinedButton(
                        onClick = onInstallPushHelper,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                    ) {
                        Text(stringResource(R.string.install_ntfy_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun NotificationOption(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
