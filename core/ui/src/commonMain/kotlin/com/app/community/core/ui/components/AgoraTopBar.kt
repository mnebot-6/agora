package com.app.community.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Barra superior con una linea de architrave dorada en el borde inferior,
 * como la banda decorativa que corona el entablamento de un templo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgoraTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    showArchitrave: Boolean = true,
) {
    val architraveColor = MaterialTheme.colorScheme.tertiary
    val architraveHeight = 2.dp

    TopAppBar(
        title = title,
        modifier = modifier.then(
            if (showArchitrave) {
                Modifier.drawBehind {
                    val h = architraveHeight.toPx()
                    drawLine(
                        color = architraveColor,
                        start = Offset(0f, size.height - h / 2f),
                        end = Offset(size.width, size.height - h / 2f),
                        strokeWidth = h,
                    )
                }
            } else {
                Modifier
            },
        ),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
