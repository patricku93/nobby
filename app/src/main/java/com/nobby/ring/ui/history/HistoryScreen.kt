package com.nobby.ring.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatDuration
import com.nobby.ring.ui.components.label
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.history_empty)) }
        } else LazyColumn(Modifier.padding(padding)) {
            items(entries) { e ->
                val mins = e.delay.inWholeMinutes
                val status = when {
                    abs(mins) < 60 -> stringResource(R.string.history_on_time)
                    mins > 0 -> stringResource(R.string.history_late, formatDuration(mins.minutes))
                    else -> stringResource(R.string.history_early, formatDuration(abs(mins).minutes))
                }
                ListItem(
                    headlineContent = { Text(e.kind.label()) },
                    supportingContent = { Text("${formatDateTime(e.confirmedAt)} · $status") },
                )
            }
        }
    }
}
