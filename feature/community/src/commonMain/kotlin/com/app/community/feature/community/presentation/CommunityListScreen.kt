package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Community
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.GreekFrame
import com.app.community.core.ui.components.GreekKeyDivider
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.StoneCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing

class CommunityListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CommunityListScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            "Comunidades",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        IconButton(onClick = { screenModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navigator.push(CreateCommunityScreen()) },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Crear comunidad")
                }
            },
        ) { padding ->
            when (val state = uiState) {
                is CommunityListScreenModel.UiState.Loading -> {
                    LoadingScreen(modifier = Modifier.padding(padding))
                }

                is CommunityListScreenModel.UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { screenModel.refresh() },
                        modifier = Modifier.padding(padding),
                    )
                }

                is CommunityListScreenModel.UiState.Content -> {
                    CommunityListContent(
                        communities = state.communities,
                        onCommunityClick = { community ->
                            navigator.push(CommunityDetailScreen(communityId = community.id))
                        },
                        onJoinClick = { navigator.push(JoinCommunityScreen()) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityListContent(
    communities: List<Community>,
    onCommunityClick: (Community) -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (communities.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            GreekFrame {
                Column(
                    modifier = Modifier.padding(AgoraSpacing.xl),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Tu agora esta vacia",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(AgoraSpacing.sm))
                    Text(
                        text = "Crea una comunidad o unete con un codigo de invitacion.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AgoraSpacing.lg))
                    AgoraButton(
                        text = "Unirse con codigo de invitacion",
                        onClick = onJoinClick,
                        variant = AgoraButtonVariant.Secondary,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
        ) {
            item {
                GreekKeyDivider()
            }
            item {
                AgoraButton(
                    text = "Unirse con codigo de invitacion",
                    onClick = onJoinClick,
                    variant = AgoraButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(communities, key = { it.id }) { community ->
                CommunityCard(
                    community = community,
                    onClick = { onCommunityClick(community) },
                )
            }
        }
    }
}

@Composable
private fun CommunityCard(
    community: Community,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StoneCard(
        modifier = modifier,
        elevation = AgoraElevation.subtle,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(AgoraSpacing.cardInternal)) {
            Text(
                text = community.name,
                style = MaterialTheme.typography.titleMedium,
            )
            if (!community.description.isNullOrBlank()) {
                Spacer(Modifier.height(AgoraSpacing.xs))
                Text(
                    text = community.description.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
