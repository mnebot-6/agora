package com.app.community.feature.activity.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Activity
import com.app.community.core.model.SlotMode
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.LoadingScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ActivityFeedScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ActivityFeedScreenModel>()
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Actividades") },
                    actions = {
                        IconButton(onClick = screenModel::load) {
                            Icon(Icons.Default.Refresh, "Refrescar")
                        }
                    },
                )
            },
        ) { padding ->
            when (val s = state) {
                is ActivityFeedUiState.Loading -> LoadingScreen(Modifier.padding(padding))
                is ActivityFeedUiState.Error -> ErrorScreen(s.message, onRetry = screenModel::load, modifier = Modifier.padding(padding))
                is ActivityFeedUiState.Content -> {
                    if (s.activities.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No hay actividades próximas en tu ágora",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { Spacer(Modifier.height(8.dp)) }
                            items(s.activities) { activity ->
                                ActivityFeedCard(
                                    activity = activity,
                                    onClick = { navigator.push(ActivityDetailScreen(activity.id)) },
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFeedCard(activity: Activity, onClick: () -> Unit) {
    val localDateTime = activity.datetime.toLocalDateTime(TimeZone.currentSystemDefault())

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val modeLabel = when (activity.slotMode) {
                    SlotMode.UNLIMITED -> "Sin límite"
                    SlotMode.LIMITED -> "${activity.maxSlots ?: 0} plazas"
                    SlotMode.LIMITED_WITH_POSITIONS -> "${activity.maxSlots ?: 0} plazas"
                }
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = buildString {
                    append(localDateTime.dayOfMonth.toString().padStart(2, '0'))
                    append("/")
                    append(localDateTime.monthNumber.toString().padStart(2, '0'))
                    append("/")
                    append(localDateTime.year)
                    append("  ")
                    append(localDateTime.hour.toString().padStart(2, '0'))
                    append(":")
                    append(localDateTime.minute.toString().padStart(2, '0'))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            activity.locationName?.let { loc ->
                Text(
                    text = loc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
