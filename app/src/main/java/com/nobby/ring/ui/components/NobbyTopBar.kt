package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Top bar on paper (background), Fredoka title (titleLarge). Same named parameters as TopAppBar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NobbyTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bg = MaterialTheme.colorScheme.background
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bg, scrolledContainerColor = bg),
    )
}
