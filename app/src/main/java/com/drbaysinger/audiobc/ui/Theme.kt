package com.drbaysinger.audiobc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0D0F14)
val Card = Color(0xFF161920)
val Line = Color(0xFF262A33)
val Accent = Color(0xFFE5A00D)
val OnAccent = Color(0xFF1A1400)
val Blue = Color(0xFF4AA3FF)
val Text = Color(0xFFE8E6E3)
val Muted = Color(0xFF9AA3AD)
val Grey = Color(0xFF6B7280)
val Danger = Color(0xFFE5484D)
val Ok = Color(0xFF3DDC97)
val AccentTint = Color(0xFF2A2210)

// Kept for older call sites.
val Surface = Card
val Surface2 = Color(0xFF1E222B)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Bg,
    onBackground = Text,
    surface = Card,
    onSurface = Text,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Color.White,
    outline = Line,
)

@Composable
fun AudioBcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
