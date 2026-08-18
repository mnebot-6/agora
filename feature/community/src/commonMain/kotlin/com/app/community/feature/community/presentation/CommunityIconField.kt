package com.app.community.feature.community.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.app.community.core.model.CommunityIcon
import com.app.community.core.ui.components.CommunityAvatar
import com.app.community.core.ui.components.vector
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.community_icon_collapse
import agora.feature.community.generated.resources.community_icon_expand
import agora.feature.community.generated.resources.community_icon_label
import agora.feature.community.generated.resources.community_icon_name_art
import agora.feature.community.generated.resources.community_icon_name_basketball
import agora.feature.community.generated.resources.community_icon_name_book
import agora.feature.community.generated.resources.community_icon_name_coffee
import agora.feature.community.generated.resources.community_icon_name_generic
import agora.feature.community.generated.resources.community_icon_name_group
import agora.feature.community.generated.resources.community_icon_name_home
import agora.feature.community.generated.resources.community_icon_name_music
import agora.feature.community.generated.resources.community_icon_name_none
import agora.feature.community.generated.resources.community_icon_name_party
import agora.feature.community.generated.resources.community_icon_name_pet
import agora.feature.community.generated.resources.community_icon_name_running
import agora.feature.community.generated.resources.community_icon_name_soccer
import agora.feature.community.generated.resources.community_icon_name_theater
import agora.feature.community.generated.resources.community_icon_name_travel
import agora.feature.community.generated.resources.community_icon_name_volleyball
import agora.feature.community.generated.resources.community_icon_name_work
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Campo inline de icono: vista previa del avatar mas la rejilla de opciones.
 *
 * Va inline y no en un dialogo porque el unico sitio donde se edita ya es un
 * dialogo, y anidar dos AlertDialog es un borde afilado en wasmJs. Por lo mismo
 * la rejilla es FlowRow y no LazyVerticalGrid: 17 celdas no ganan nada siendo
 * lazy, y un layout no-lazy si puede vivir dentro de un padre con scroll.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityIconField(
    communityId: String,
    name: String,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Plegada por defecto: desplegada ocupa ~368dp y el dialogo de edicion solo
    // tiene 480dp, asi que empujaria nombre, descripcion y el resto bajo la linea
    // de flotacion. El avatar de la cabecera ya enseña el icono actual sin abrir.
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(
                        if (expanded) Res.string.community_icon_collapse else Res.string.community_icon_expand,
                    ),
                    role = Role.Button,
                ) { expanded = !expanded },
        ) {
            CommunityAvatar(
                communityId = communityId,
                name = name,
                iconKey = selectedKey,
                size = 48.dp,
            )
            Spacer(Modifier.width(AgoraSpacing.md))
            Text(
                text = stringResource(Res.string.community_icon_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(AgoraSpacing.xs))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                // La fila ya lleva el texto visible y el onClickLabel: el galon decora.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            Spacer(Modifier.height(AgoraSpacing.sm))

            FlowRow(
                maxItemsInEachRow = 4,
                horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
            ) {
                CommunityIcon.entries.forEach { icon ->
                    IconTile(
                        image = icon.vector(),
                        label = stringResource(icon.nameRes()),
                        isSelected = icon.key == selectedKey,
                        enabled = enabled,
                        onClick = { onSelect(icon.key) },
                    )
                }
                // "Sin icono" es una opcion mas de la rejilla, no un boton del dialogo:
                // asi borrar el icono no ocupa el slot visualmente dominante.
                IconTile(
                    image = Icons.Default.Clear,
                    label = stringResource(Res.string.community_icon_name_none),
                    isSelected = selectedKey == null,
                    enabled = enabled,
                    onClick = { onSelect(null) },
                )
            }
        }
    }
}

@Composable
private fun IconTile(
    image: ImageVector,
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            // Los demas controles del formulario son Material3 y se atenuan solos;
            // esta celda es propia, asi que aplica el alfa estandar a mano.
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = isSelected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = image,
            contentDescription = label,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Nombre localizado. La key se persiste y es inglesa: nunca se le muestra al usuario. */
private fun CommunityIcon.nameRes(): StringResource = when (this) {
    CommunityIcon.VOLLEYBALL -> Res.string.community_icon_name_volleyball
    CommunityIcon.SOCCER -> Res.string.community_icon_name_soccer
    CommunityIcon.BASKETBALL -> Res.string.community_icon_name_basketball
    CommunityIcon.RUNNING -> Res.string.community_icon_name_running
    CommunityIcon.GROUP -> Res.string.community_icon_name_group
    CommunityIcon.HOME -> Res.string.community_icon_name_home
    CommunityIcon.COFFEE -> Res.string.community_icon_name_coffee
    CommunityIcon.PARTY -> Res.string.community_icon_name_party
    CommunityIcon.MUSIC -> Res.string.community_icon_name_music
    CommunityIcon.THEATER -> Res.string.community_icon_name_theater
    CommunityIcon.BOOK -> Res.string.community_icon_name_book
    CommunityIcon.ART -> Res.string.community_icon_name_art
    CommunityIcon.WORK -> Res.string.community_icon_name_work
    CommunityIcon.TRAVEL -> Res.string.community_icon_name_travel
    CommunityIcon.PET -> Res.string.community_icon_name_pet
    CommunityIcon.GENERIC -> Res.string.community_icon_name_generic
}
