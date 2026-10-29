package com.nobby.ring

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.ui.MainViewModel
import com.nobby.ring.ui.navigation.NobbyNavHost
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.isDark
import dagger.hilt.android.AndroidEntryPoint

private val DARK_SCRIM = Color.argb(0x99, 0x1B, 0x1B, 0x1B)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val start by viewModel.startDestination.collectAsStateWithLifecycle()
            val dark = theme.isDark()
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, DARK_SCRIM)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NobbyTheme(theme) {
                start?.let { NobbyNavHost(it) }
            }
        }
    }
}
