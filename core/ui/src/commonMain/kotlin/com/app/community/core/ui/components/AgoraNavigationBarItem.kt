package com.app.community.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.IonicCapitalIndicatorShape

/**
 * Item de la bottom navbar con indicador de tab seleccionada en forma de capitel jonico
 * (trapecio dorado bajo el icono). Reemplaza el indicator pill horizontal default de
 * Material 3 NavigationBarItem por algo coherente con la estetica griega de la app.
 *
 * Sin label: solo el icono + el indicador cuando esta seleccionado.
 */
@Composable
fun RowScope.AgoraNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    // Sin fillMaxHeight: el NavigationBar ya impone minHeight = 80dp, dejamos que
    // el Box se ajuste a la altura intrinseca del contenido para evitar que el
    // navbar reclame todo el alto de la pantalla.
    Box(
        modifier = Modifier
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (selected) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                icon()
            }
            Spacer(Modifier.height(4.dp))
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                Box(
                    Modifier
                        .size(width = 14.dp, height = 6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = IonicCapitalIndicatorShape,
                        ),
                )
            }
        }
    }
}
