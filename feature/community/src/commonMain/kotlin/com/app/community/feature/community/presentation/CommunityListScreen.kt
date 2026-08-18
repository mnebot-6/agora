package com.app.community.feature.community.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.app.community.core.ui.components.CommunityAvatar
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IonicFrame {
                Column(
                    modifier = Modifier.padding(AgoraSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                CommunityAvatar(
                    communityId = community.id,
                    name = community.name,
                    iconKey = community.iconKey,
                    size = 40.dp,
                )
                Spacer(Modifier.width(AgoraSpacing.md))
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!community.description.isNullOrBlank()) {
                Spacer(Modifier.height(AgoraSpacing.sm))
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
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    // Linea de conexion: la senal que dice "esto cuelga de lo de arriba".
                    Spacer(Modifier.width(AgoraSpacing.lg))
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        node.children.forEach { child ->
                            NestedChildRow(
                                community = child,
                                parentName = community.name,
                                onClick = { onClick(child.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NestedChildRow(
    community: Community,
    parentName: String,
    onClick: () -> Unit,
) {
    val childCd = stringResource(Res.string.community_list_child_cd, community.name, parentName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(start = AgoraSpacing.md, top = AgoraSpacing.sm, bottom = AgoraSpacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = childCd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommunityAvatar(
            communityId = community.id,
            name = community.name,
            iconKey = community.iconKey,
            size = 24.dp,
        )
        Spacer(Modifier.width(AgoraSpacing.sm))
        Text(
            text = community.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
