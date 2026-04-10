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
 * Barra superior compacta con linea dentil sutil en el borde inferior,
 * evocando la cornisa refinada del entablamento jonico.
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
    val architraveHeight = 1.5.dp

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
        expandedHeight = 48.dp,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
