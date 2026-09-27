package com.solanasignal.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BuyGreen = Color(0xFF22C55E)
val SellRed = Color(0xFFEF4444)
val WatchAmber = Color(0xFFF59E0B)
val BgDark = Color(0xFF0B0E14)
val SurfaceDark = Color(0xFF141A24)
val SurfaceRaised = Color(0xFF1B2330)
val TextMuted = Color(0xFF8B93A7)
val NeutralBlue = Color(0xFF60A5FA)

private val DarkColors = darkColorScheme(
    primary = BuyGreen,
    secondary = WatchAmber,
    background = BgDark,
    surface = SurfaceDark,
    error = SellRed
)

@Composable
fun SolanaSignalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
