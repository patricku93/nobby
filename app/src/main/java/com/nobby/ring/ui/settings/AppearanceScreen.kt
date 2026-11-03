package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.components.BeadRing
import com.nobby.ring.ui.components.CardTone
import com.nobby.ring.ui.components.ChipTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.PhaseChip
import com.nobby.ring.ui.components.PresetPicker
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.SegmentedChoice
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyType

/** Bundle order for the mode chips: Hell, Dunkel, Auto. */
val MODE_ORDER: List<ThemeMode> = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        NobbyTopBar(
            title = { Text(stringResource(R.string.settings_appearance)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        AppearanceContent(theme, viewModel::setTheme, Modifier.padding(padding))
    }
}

/** Live preview (the whole app re-themes as soon as a preset is saved), five presets, Light / Dark / Auto. */
@Composable
fun AppearanceContent(theme: ThemeSpec, onChange: (ThemeSpec) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
        verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
    ) {
        MiniHomePreview()
        Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.titleMedium)
        PresetPicker(theme.palette, { onChange(theme.copy(palette = it)) })
        Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
        SegmentedChoice(MODE_ORDER, theme.mode, { onChange(theme.copy(mode = it)) }, label = { it.label() })
    }
}

/** A tiny Home: ring with sticker, phase chips and the Done button. Decorative, hidden from accessibility services. */
@Composable
private fun MiniHomePreview() {
    val previewLabel = stringResource(R.string.appearance_preview)
    NobbyCard(Modifier.clearAndSetSemantics { }, tone = CardTone.FLAT) {
        Text(previewLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BeadRing(RingTone.WEAR, beads = 21, done = 11, modifier = Modifier.size(120.dp), sticker = { s -> Nobby(MascotMood.HAPPY, s) }) {
                Text("10", style = NobbyType.countdown.copy(fontSize = 40.sp, lineHeight = 40.sp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Short chip texts: the phase names alone fit beside the ring at 360 dp / font scale 1.3.
                PhaseChip(stringResource(R.string.phase_wear), ChipTone.WEAR)
                PhaseChip(stringResource(R.string.phase_pause), ChipTone.PAUSE)
            }
        }
        // Own line, so the label never wraps mid-word next to the ring.
        NobbyButton(onClick = {}, Modifier.padding(top = 12.dp), icon = Icons.Rounded.Check) {
            Text(stringResource(R.string.action_done), maxLines = 1, softWrap = false)
        }
    }
}
