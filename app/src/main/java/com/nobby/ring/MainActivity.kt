package com.nobby.ring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.ui.MainViewModel
import com.nobby.ring.ui.navigation.NobbyNavHost
import com.nobby.ring.ui.theme.NobbyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val start by viewModel.startDestination.collectAsStateWithLifecycle()
            NobbyTheme(theme) {
                start?.let { NobbyNavHost(it) }
            }
        }
    }
}
