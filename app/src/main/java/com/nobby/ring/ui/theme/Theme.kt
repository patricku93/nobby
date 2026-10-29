package com.nobby.ring.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec

@Composable
fun ThemeSpec.isDark(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun NobbyTheme(theme: ThemeSpec, content: @Composable () -> Unit) {
    val (light, dark) = remember(theme.palette) { paletteSchemes(theme.palette) }
    MaterialTheme(
        colorScheme = if (theme.isDark()) dark else light,
        typography = NobbyTypography,
        content = content,
    )
}
