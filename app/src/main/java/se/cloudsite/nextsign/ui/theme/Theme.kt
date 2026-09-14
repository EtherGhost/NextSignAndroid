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

// Shared status colors: blue means a signature is still owed by someone, green means
// there's nothing left to do. Used wherever a document/signer status needs a color.
val NextSignGreen = Color(0xFF5A8F3C)

// Neutral: this document doesn't involve this account as a signer at all (e.g. one it
// created/requested but never added itself to) - distinct from green, which specifically
// means "your part is done", not "you were never part of this".
val NextSignGrey = Color(0xFF757575)

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
