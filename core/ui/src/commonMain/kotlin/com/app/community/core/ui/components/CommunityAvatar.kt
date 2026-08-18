package com.app.community.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.model.CommunityIcon
import com.app.community.core.model.avatarColorIndex

/** Traduce una clave persistida a su dibujo. Único punto de acoplamiento clave-icono. */
fun CommunityIcon.vector(): ImageVector = when (this) {
    CommunityIcon.VOLLEYBALL -> Icons.Default.SportsVolleyball
    CommunityIcon.SOCCER -> Icons.Default.SportsSoccer
    CommunityIcon.BASKETBALL -> Icons.Default.SportsBasketball
    CommunityIcon.RUNNING -> Icons.AutoMirrored.Filled.DirectionsRun
    CommunityIcon.GROUP -> Icons.Default.Groups
    CommunityIcon.HOME -> Icons.Default.Home
    CommunityIcon.COFFEE -> Icons.Default.LocalCafe
    CommunityIcon.PARTY -> Icons.Default.Celebration
    CommunityIcon.MUSIC -> Icons.Default.MusicNote
    CommunityIcon.THEATER -> Icons.Default.TheaterComedy
    CommunityIcon.BOOK -> Icons.AutoMirrored.Filled.MenuBook
    CommunityIcon.ART -> Icons.Default.Palette
    CommunityIcon.WORK -> Icons.Default.Work
    CommunityIcon.TRAVEL -> Icons.Default.Flight
    CommunityIcon.PET -> Icons.Default.Pets
    CommunityIcon.GENERIC -> Icons.Default.Public
}

/**
 * Avatar cuadrado redondeado de una comunidad. Es la firma visual que la
 * distingue de una actividad: una actividad NUNCA lleva este avatar.
 *
 * Sin icono elegido cae a la inicial del nombre sobre un color derivado del id,
 * de forma que comunidades distintas se distingan entre si la mayoria de las veces.
 *
 * `contentDescription` es null por defecto porque el uso mas comun (filas de
 * lista) ya trae el nombre de la comunidad como texto visible al lado: el
 * avatar es decorativo ahi. Un caller sin texto adyacente debe pasarlo.
 */
@Composable
fun CommunityAvatar(
    communityId: String,
    name: String,
    iconKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    // Un solo listado de pares: fondo y contenido no pueden desincronizarse.
    // El modulo protege el indice aunque el tamano de la paleta cambie.
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val (background, foreground) = palette[avatarColorIndex(communityId) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val icon = CommunityIcon.fromKey(iconKey)
        if (icon != null) {
            Icon(
                imageVector = icon.vector(),
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size(size * 0.55f),
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty().ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = foreground,
                modifier = if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            )
        }
    }
}
