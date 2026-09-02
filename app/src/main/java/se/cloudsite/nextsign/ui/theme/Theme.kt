package se.cloudsite.nextsign.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import se.cloudsite.nextsign.util.ThemeMode

// Matches the app icon's background blue (see ic_launcher_background /
// assets/logo.svg in the Ubuntu Touch app).
val NextSignBlue = Color(0xFF1F6FEB)

private val LightColors = lightColorScheme(primary = NextSignBlue)
private val DarkColors = darkColorScheme(primary = NextSignBlue)

@Composable
fun NextSignTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (useDarkTheme) DarkColors else LightColors, content = content)
}
