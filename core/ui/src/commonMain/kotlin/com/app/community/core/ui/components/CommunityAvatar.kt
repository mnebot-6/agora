package com.app.community.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.community.core.model.AVATAR_COLOR_COUNT
import com.app.community.core.model.CommunityIcon
import com.app.community.core.model.avatarColorIndex

/** Traduce una clave persistida a su dibujo. Único punto de acoplamiento clave-icono. */
fun CommunityIcon.vector(): ImageVector = when (this) {
    CommunityIcon.VOLLEYBALL -> Icons.Default.SportsVolleyball
    CommunityIcon.SOCCER -> Icons.Default.SportsSoccer
    CommunityIcon.BASKETBALL -> Icons.Default.SportsBasketball
    CommunityIcon.RUNNING -> Icons.Default.DirectionsRun
    CommunityIcon.GROUP -> Icons.Default.Groups
    CommunityIcon.HOME -> Icons.Default.Home
    CommunityIcon.COFFEE -> Icons.Default.LocalCafe
    CommunityIcon.PARTY -> Icons.Default.Celebration
    CommunityIcon.MUSIC -> Icons.Default.MusicNote
    CommunityIcon.THEATER -> Icons.Default.TheaterComedy
    CommunityIcon.BOOK -> Icons.Default.MenuBook
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
 * de forma que dos comunidades distintas nunca se ven iguales.
 */
@Composable
fun CommunityAvatar(
    communityId: String,
    name: String,
    iconKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    // Debe tener AVATAR_COLOR_COUNT entradas: avatarColorIndex() devuelve un
    // índice en 0 until AVATAR_COLOR_COUNT y se usa directamente contra esta lista.
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )
    val onPalette = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    check(palette.size == AVATAR_COLOR_COUNT && onPalette.size == AVATAR_COLOR_COUNT)
    val index = avatarColorIndex(communityId)
    val background: Color = palette[index]
    val foreground: Color = onPalette[index]

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
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(size * 0.55f),
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = foreground,
            )
        }
    }
}
