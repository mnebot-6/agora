package com.app.community.core.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.NavBarShape

/**
 * Barra de navegacion inferior con estetica de base de columnata jonica:
 * linea dorada sutil en el borde superior y forma
 * con esquinas superiores elegantemente redondeadas.
 */
@Composable
fun AgoraNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.tertiary
    val lineHeight = 1.dp

    NavigationBar(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = NavBarShape, clip = false)
            .clip(NavBarShape)
            .drawBehind {
                val h = lineHeight.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(0f, h / 2f),
                    end = Offset(size.width, h / 2f),
                    strokeWidth = h,
                )
            },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}
