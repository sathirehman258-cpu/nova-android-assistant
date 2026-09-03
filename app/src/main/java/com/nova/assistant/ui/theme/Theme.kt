package com.nova.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NovaPurple = Color(0xFF6C4CF1)
private val NovaPurpleDark = Color(0xFF4B2FD1)
private val NovaTeal = Color(0xFF35D0BA)
private val NovaBackgroundDark = Color(0xFF0E0B1A)
private val NovaBackgroundLight = Color(0xFFFAFAFC)

private val DarkColors = darkColorScheme(
    primary = NovaPurple,
    secondary = NovaTeal,
    background = NovaBackgroundDark,
    surface = Color(0xFF1A1626)
)

private val LightColors = lightColorScheme(
    primary = NovaPurpleDark,
    secondary = NovaTeal,
    background = NovaBackgroundLight,
    surface = Color.White
)

@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
