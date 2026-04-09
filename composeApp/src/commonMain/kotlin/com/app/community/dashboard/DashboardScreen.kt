package com.app.community.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.SlotMode
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.GreekFrame
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.SlotStatusBadge
import com.app.community.core.ui.components.StoneCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.slotStatusColors
import com.app.community.feature.activity.presentation.ActivityDetailScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DashboardScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<DashboardScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            "Agora",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    actions = {
                        IconButton(onClick = { screenModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                        }
                    },
                )
            },
        ) { padding ->
            when (val state = uiState) {
                is DashboardScreenModel.UiState.Loading -> {
                    LoadingScreen(modifier = Modifier.padding(padding))
                }

                is DashboardScreenModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { screenModel.refresh() },
                        modifier = Modifier.padding(padding),
                    )
                }

                is DashboardScreenModel.UiState.Content -> {
                    DashboardContent(
                        state = state,
                        onActivityClick = { activity ->
                            navigator.push(ActivityDetailScreen(activityId = activity.id))
                        },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardScreenModel.UiState.Content,
    onActivityClick: (Activity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.upcomingActivities.isEmpty()) {
        EmptyDashboard(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AgoraSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
    ) {
        // Hero card -- next activity
        val next = state.nextActivity
        if (next != null) {
            item(key = "hero") {
                HeroActivityCard(
                    info = next,
                    onClick = { onActivityClick(next.activity) },
                )
            }
        }

        // Upcoming activities header
        if (state.upcomingActivities.size > 1) {
            item(key = "header") {
                FriezeBandHeader(
                    title = "Pr\u00f3ximas actividades",
                    modifier = Modifier.padding(top = AgoraSpacing.xs),
                )
            }

            // Compact cards for remaining activities (skip the first which is the hero)
            items(
                state.upcomingActivities.drop(1),
                key = { it.activity.id },
            ) { info ->
                CompactActivityCard(
                    info = info,
                    onClick = { onActivityClick(info.activity) },
                )
            }
        }
    }
}

@Composable
private fun HeroActivityCard(
    info: ActivityWithSlotInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = info.activity
    val localDt = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())
    val slotColors = MaterialTheme.slotStatusColors

    StoneCard(
        modifier = modifier,
        showGreekBorder = true,
        elevation = AgoraElevation.hero,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.heroCardInternal)) {
            // Activity name
            Text(
                text = activity.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(AgoraSpacing.md))

            // Date/time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(AgoraSpacing.xs + AgoraSpacing.xxs))
                Text(
                    text = "${dayOfWeekName(localDt.dayOfWeek)} ${localDt.dayOfMonth}, ${localDt.hour.toString().padStart(2, '0')}:${localDt.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Location
            activity.locationName?.let { loc ->
                Spacer(Modifier.height(AgoraSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AgoraSpacing.xs + AgoraSpacing.xxs))
                    Text(
                        text = loc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(AgoraSpacing.lg))

            // Status badge
            val (badgeColorPair, badgeText) = when {
                info.isUserReserved -> slotColors.reservedByMe to "Reservado"
                info.userQueuePosition != null -> slotColors.reservedByOther to "En cola #${info.userQueuePosition}"
                else -> slotColors.available to "No reservado"
            }

            SlotStatusBadge(
                text = badgeText,
                colorPair = badgeColorPair,
            )

            // Slot counter (only for limited modes)
            if (activity.slotMode != SlotMode.UNLIMITED && info.availableSlots >= 0) {
                Spacer(Modifier.height(AgoraSpacing.lg))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${info.availableSlots}",
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (info.availableSlots > 0) {
                            slotColors.available.content
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Spacer(Modifier.width(AgoraSpacing.sm))
                    Text(
                        text = if (info.availableSlots == 1) "plaza libre" else "plazas libres",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = AgoraSpacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactActivityCard(
    info: ActivityWithSlotInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = info.activity
    val localDt = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())
    val slotColors = MaterialTheme.slotStatusColors

    StoneCard(
        modifier = modifier,
        elevation = AgoraElevation.none,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .padding(AgoraSpacing.md)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${dayOfWeekAbbr(localDt.dayOfWeek)} ${localDt.dayOfMonth}, ${localDt.hour.toString().padStart(2, '0')}:${localDt.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                activity.locationName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(AgoraSpacing.sm))

            // Status chip
            val (chipColorPair, chipText) = when {
                info.isUserReserved -> slotColors.reservedByMe to "Reservado"
                info.availableSlots == 0 && activity.slotMode != SlotMode.UNLIMITED -> {
                    slotColors.reservedByOther to "Lleno"
                }
                activity.slotMode == SlotMode.UNLIMITED -> {
                    slotColors.available to "Abierto"
                }
                else -> slotColors.available to "${info.availableSlots} plazas"
            }

            SlotStatusBadge(
                text = chipText,
                colorPair = chipColorPair,
                isCompact = true,
            )
        }
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier) {
    GreekFrame(
        modifier = modifier
            .fillMaxSize()
            .padding(AgoraSpacing.xxl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AgoraSpacing.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Tu \u00e1gora est\u00e1 vac\u00eda",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(AgoraSpacing.sm))
            Text(
                text = "\u00danete a una comunidad para ver actividades.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun dayOfWeekName(dow: kotlinx.datetime.DayOfWeek): String = when (dow) {
    kotlinx.datetime.DayOfWeek.MONDAY -> "Lunes"
    kotlinx.datetime.DayOfWeek.TUESDAY -> "Martes"
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Mi\u00e9rcoles"
    kotlinx.datetime.DayOfWeek.THURSDAY -> "Jueves"
    kotlinx.datetime.DayOfWeek.FRIDAY -> "Viernes"
    kotlinx.datetime.DayOfWeek.SATURDAY -> "S\u00e1bado"
    kotlinx.datetime.DayOfWeek.SUNDAY -> "Domingo"
    else -> dow.name
}

private fun dayOfWeekAbbr(dow: kotlinx.datetime.DayOfWeek): String = when (dow) {
    kotlinx.datetime.DayOfWeek.MONDAY -> "Lun"
    kotlinx.datetime.DayOfWeek.TUESDAY -> "Mar"
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Mi\u00e9"
    kotlinx.datetime.DayOfWeek.THURSDAY -> "Jue"
    kotlinx.datetime.DayOfWeek.FRIDAY -> "Vie"
    kotlinx.datetime.DayOfWeek.SATURDAY -> "S\u00e1b"
    kotlinx.datetime.DayOfWeek.SUNDAY -> "Dom"
    else -> dow.name.take(3)
}
