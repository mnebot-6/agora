package com.app.community.feature.community.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.app.community.core.ui.components.AgoraFabMenu
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FabMenuItem
import com.app.community.core.ui.components.IonicFrame
import com.app.community.core.ui.components.DentilDivider
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class CommunityListScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CommunityListScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        LaunchedEffect(Unit) { screenModel.refresh() }

        val joinLabel = stringResource(Res.string.community_list_join_button)
        val exploreLabel = stringResource(Res.string.explore_title)
        val createLabel = stringResource(Res.string.create_community_title)

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            stringResource(Res.string.community_list_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
            },
            floatingActionButton = {
                AgoraFabMenu(
                    fabIcon = Icons.Default.Public,
                    fabContentDescription = stringResource(Res.string.community_list_create_cd),
                    items = listOf(
                        FabMenuItem(
                            icon = Icons.Default.GroupAdd,
                            label = joinLabel,
                            onClick = { navigator.push(JoinCommunityScreen()) },
                        ),
                        FabMenuItem(
                            icon = Icons.Default.Search,
                            label = exploreLabel,
                            onClick = { navigator.push(ExploreCommunitiesScreen()) },
                        ),
                        FabMenuItem(
                            icon = Icons.Default.Groups,
                            label = createLabel,
                            onClick = { navigator.push(CreateCommunityScreen()) },
                        ),
                    ),
                )
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
                        nodes = state.nodes,
                        onCommunityClick = { id ->
                            navigator.push(CommunityDetailScreen(communityId = id))
                        },
                        onJoinClick = { navigator.push(JoinCommunityScreen()) },
                        onExploreClick = { navigator.push(ExploreCommunitiesScreen()) },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityListContent(
    nodes: List<CommunityNode>,
    onCommunityClick: (String) -> Unit,
    onJoinClick: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            IonicFrame {
                Column(
                    modifier = Modifier.padding(AgoraSpacing.xl),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.community_list_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(AgoraSpacing.sm))
                    Text(
                        text = stringResource(Res.string.community_list_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AgoraSpacing.lg))
                    AgoraButton(
                        text = stringResource(Res.string.community_list_join_button),
                        onClick = onJoinClick,
                        variant = AgoraButtonVariant.Secondary,
                    )
                    Spacer(Modifier.height(AgoraSpacing.sm))
                    AgoraButton(
                        text = stringResource(Res.string.explore_title),
                        onClick = onExploreClick,
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
            item { DentilDivider() }
            items(nodes, key = { it.community.id }) { node ->
                CommunityCard(
                    node = node,
                    onClick = onCommunityClick,
                )
            }
        }
    }
}

@Composable
private fun CommunityCard(
    node: CommunityNode,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val community = node.community
    MarbleCard(
        modifier = modifier,
        elevation = AgoraElevation.subtle,
        onClick = { onClick(community.id) },
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

            if (node.children.isNotEmpty()) {
                Spacer(Modifier.height(AgoraSpacing.md))
                node.children.forEach { child ->
                    NestedChildRow(
                        community = child,
                        onClick = { onClick(child.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NestedChildRow(
    community: Community,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AgoraSpacing.sm, horizontal = AgoraSpacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
    ) {
        Icon(
            Icons.Default.SubdirectoryArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = community.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            community.memberCount?.let { count ->
                Text(
                    text = stringResource(Res.string.community_detail_members_header, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
