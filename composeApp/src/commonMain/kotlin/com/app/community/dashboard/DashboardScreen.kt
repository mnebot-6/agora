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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import agora.composeapp.generated.resources.Res
import agora.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
import com.app.community.core.ui.components.IonicFrame
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.SlotStatusBadge
import com.app.community.core.ui.components.MarbleCard
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

        LaunchedEffect(Unit) { screenModel.refresh() }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            "Agora",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AgoraSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
    ) {
        // Saludo + resumen semanal (siempre visible, independiente de actividades)
        item(key = "greeting") {
            DashboardGreeting(
                displayName = state.displayName,
                weekActivityCount = state.weekActivityCount,
                weekConfirmedCount = state.weekConfirmedCount,
                modifier = Modifier.padding(vertical = AgoraSpacing.sm),
            )
        }

        if (state.upcomingActivities.isEmpty()) {
            item(key = "empty") {
                EmptyDashboard()
            }
            return@LazyColumn
        }

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
                    title = stringResource(Res.string.dashboard_upcoming_activities),
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

    MarbleCard(
        modifier = modifier,
        showDentilBorder = true,
        elevation = AgoraElevation.hero,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(AgoraSpacing.heroCardInternal).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: activity info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(AgoraSpacing.sm))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(AgoraSpacing.xs))
                    Text(
                        text = "${dayOfWeekAbbr(localDt.dayOfWeek)} ${localDt.dayOfMonth}, ${localDt.hour.toString().padStart(2, '0')}:${localDt.minute.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                activity.locationName?.let { loc ->
                    Spacer(Modifier.height(AgoraSpacing.xxs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(AgoraSpacing.xs))
                        Text(
                            text = loc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(AgoraSpacing.sm))

                val (badgeColorPair, badgeText) = when {
                    info.isUserReserved -> slotColors.reservedByMe to stringResource(Res.string.dashboard_status_reserved)
                    info.userQueuePosition != null -> slotColors.reservedByOther to stringResource(Res.string.dashboard_status_in_queue, info.userQueuePosition!!)
                    else -> slotColors.available to stringResource(Res.string.dashboard_status_not_reserved)
                }

                SlotStatusBadge(
                    text = badgeText,
                    colorPair = badgeColorPair,
                    isCompact = true,
                )
            }

            // Right: slot counter
            if (activity.slotMode != SlotMode.UNLIMITED && info.availableSlots >= 0) {
                Spacer(Modifier.width(AgoraSpacing.md))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${info.availableSlots}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (info.availableSlots > 0) {
                            slotColors.available.content
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        text = pluralStringResource(Res.plurals.dashboard_slots_available, info.availableSlots),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
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

    MarbleCard(
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
                info.isUserReserved -> slotColors.reservedByMe to stringResource(Res.string.dashboard_status_reserved)
                info.availableSlots == 0 && activity.slotMode != SlotMode.UNLIMITED -> {
                    slotColors.reservedByOther to stringResource(Res.string.dashboard_status_full)
                }
                activity.slotMode == SlotMode.UNLIMITED -> {
                    slotColors.available to stringResource(Res.string.dashboard_status_open)
                }
                else -> slotColors.available to stringResource(Res.string.dashboard_status_slots, info.availableSlots)
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
    IonicFrame(
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
                text = stringResource(Res.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(AgoraSpacing.sm))
            Text(
                text = stringResource(Res.string.dashboard_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun dayOfWeekName(dow: kotlinx.datetime.DayOfWeek): String = when (dow) {
    kotlinx.datetime.DayOfWeek.MONDAY -> stringResource(Res.string.day_mon)
    kotlinx.datetime.DayOfWeek.TUESDAY -> stringResource(Res.string.day_tue)
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> stringResource(Res.string.day_wed)
    kotlinx.datetime.DayOfWeek.THURSDAY -> stringResource(Res.string.day_thu)
    kotlinx.datetime.DayOfWeek.FRIDAY -> stringResource(Res.string.day_fri)
    kotlinx.datetime.DayOfWeek.SATURDAY -> stringResource(Res.string.day_sat)
    kotlinx.datetime.DayOfWeek.SUNDAY -> stringResource(Res.string.day_sun)
    else -> dow.name
}

@Composable
private fun dayOfWeekAbbr(dow: kotlinx.datetime.DayOfWeek): String = when (dow) {
    kotlinx.datetime.DayOfWeek.MONDAY -> stringResource(Res.string.day_mon_abbr)
    kotlinx.datetime.DayOfWeek.TUESDAY -> stringResource(Res.string.day_tue_abbr)
    kotlinx.datetime.DayOfWeek.WEDNESDAY -> stringResource(Res.string.day_wed_abbr)
    kotlinx.datetime.DayOfWeek.THURSDAY -> stringResource(Res.string.day_thu_abbr)
    kotlinx.datetime.DayOfWeek.FRIDAY -> stringResource(Res.string.day_fri_abbr)
    kotlinx.datetime.DayOfWeek.SATURDAY -> stringResource(Res.string.day_sat_abbr)
    kotlinx.datetime.DayOfWeek.SUNDAY -> stringResource(Res.string.day_sun_abbr)
    else -> dow.name.take(3)
}
