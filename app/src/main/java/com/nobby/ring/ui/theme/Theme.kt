package com.nobby.ring.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    val dark = theme.isDark()
    val scheme = remember(theme.palette, dark) { theme.palette.colorScheme(dark) }
    val extra = remember(theme.palette, dark) { theme.palette.extraColors(dark) }
    CompositionLocalProvider(LocalNobbyExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = NobbyTypography, content = content)
    }
}
