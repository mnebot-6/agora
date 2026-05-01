package com.app.community.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.app.community.core.ui.theme.AgoraSpacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import agora.composeapp.generated.resources.Res
import agora.composeapp.generated.resources.dashboard_greeting_afternoon
import agora.composeapp.generated.resources.dashboard_greeting_morning
import agora.composeapp.generated.resources.dashboard_greeting_night
import agora.composeapp.generated.resources.dashboard_greeting_with_name
import agora.composeapp.generated.resources.dashboard_week_summary
import agora.composeapp.generated.resources.dashboard_week_summary_zero

/**
 * Header del dashboard: saludo personalizado segun la hora + resumen
 * de la semana. Sin gamificacion en V1.
 */
@Composable
fun DashboardGreeting(
    displayName: String?,
    weekActivityCount: Int,
    weekConfirmedCount: Int,
    modifier: Modifier = Modifier,
) {
    val hour = currentHour()
    val greetingRes = when {
        hour in 5..11 -> Res.string.dashboard_greeting_morning
        hour in 12..19 -> Res.string.dashboard_greeting_afternoon
        else -> Res.string.dashboard_greeting_night
    }
    val greeting = stringResource(greetingRes)
    val title = if (!displayName.isNullOrBlank()) {
        stringResource(Res.string.dashboard_greeting_with_name, greeting, displayName)
    } else {
        greeting
    }

    val summary = if (weekActivityCount == 0) {
        stringResource(Res.string.dashboard_week_summary_zero)
    } else {
        stringResource(
            Res.string.dashboard_week_summary,
            weekActivityCount,
            weekConfirmedCount,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(AgoraSpacing.xs))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun currentHour(): Int {
    // Hora local en momento de composicion. No se actualiza durante la sesion
    // (no merece la pena: si saludas a las 11:59 y son las 12:00, no pasa nada).
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .hour
}
