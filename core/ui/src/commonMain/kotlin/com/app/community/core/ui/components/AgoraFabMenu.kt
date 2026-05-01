package com.app.community.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.community.core.ui.theme.AgoraSpacing
import kotlinx.coroutines.launch

enum class FabMenuItemVariant { Normal, Danger }

data class FabMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val variant: FabMenuItemVariant = FabMenuItemVariant.Normal,
    val enabled: Boolean = true,
    val badge: Int? = null,
)

/**
 * FAB que al pulsar abre un ModalBottomSheet con una lista de acciones.
 * Mantiene la pantalla limpia y agrupa acciones secundarias bajo un único punto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgoraFabMenu(
    items: List<FabMenuItem>,
    modifier: Modifier = Modifier,
    fabIcon: ImageVector = Icons.Default.Add,
    fabContentDescription: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    FloatingActionButton(
        onClick = { open = true },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(fabIcon, contentDescription = fabContentDescription)
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AgoraSpacing.sm,
                        vertical = AgoraSpacing.sm,
                    ),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
            ) {
                items.forEach { item ->
                    FabMenuRow(
                        item = item,
                        onSelect = {
                            scope.launch {
                                sheetState.hide()
                                open = false
                                item.onClick()
                            }
                        },
                    )
                }
                // Espacio inferior para safe area
                Spacer(Modifier.height(AgoraSpacing.lg))
            }
        }
    }
}

@Composable
private fun FabMenuRow(
    item: FabMenuItem,
    onSelect: () -> Unit,
) {
    val tint = when (item.variant) {
        FabMenuItemVariant.Normal -> MaterialTheme.colorScheme.onSurface
        FabMenuItemVariant.Danger -> MaterialTheme.colorScheme.error
    }
    TextButton(
        onClick = onSelect,
        enabled = item.enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AgoraSpacing.sm, horizontal = AgoraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.md),
        ) {
            Icon(item.icon, contentDescription = null, tint = tint)
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            item.badge?.let { count ->
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = AgoraSpacing.sm, vertical = AgoraSpacing.xxs),
                    )
                }
            }
        }
    }
}
