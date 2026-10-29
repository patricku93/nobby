package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import kotlinx.datetime.LocalDateTime

@Composable
fun EventCard(kind: EventKind, plannedAt: LocalDateTime, overdue: Boolean, onDone: (() -> Unit)?, modifier: Modifier = Modifier) {
    val container = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(formatDateTime(plannedAt), style = MaterialTheme.typography.bodyMedium)
            }
            if (onDone != null) Button(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        }
    }
}
