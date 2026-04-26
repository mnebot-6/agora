package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.Community
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.back_cd
import agora.feature.community.generated.resources.explore_filter_all
import agora.feature.community.generated.resources.explore_no_results
import agora.feature.community.generated.resources.explore_search_hint
import agora.feature.community.generated.resources.explore_title
import agora.feature.community.generated.resources.preview_member_count
import org.jetbrains.compose.resources.stringResource

class ExploreCommunitiesScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ExploreCommunitiesScreenModel>()
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            stringResource(Res.string.explore_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back_cd),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AgoraSpacing.screenHorizontal),
            ) {
                Spacer(Modifier.height(AgoraSpacing.md))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { screenModel.onQueryChange(it) },
                    label = { Text(stringResource(Res.string.explore_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AgoraSpacing.sm))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                ) {
                    FilterChip(
                        selected = state.selectedTagIds.isEmpty(),
                        onClick = { screenModel.clearTags() },
                        label = { Text(stringResource(Res.string.explore_filter_all)) },
                    )
                    state.availableTags.forEach { tag ->
                        val selected = state.selectedTagIds.contains(tag.id)
                        FilterChip(
                            selected = selected,
                            onClick = { screenModel.onTagToggle(tag.id) },
                            label = {
                                val icon = tag.icon?.let { "$it " }.orEmpty()
                                Text(icon + tag.nameEs)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(AgoraSpacing.md))

                when {
                    state.isLoading -> LoadingScreen()
                    state.error != null -> ErrorScreen(
                        message = state.error!!,
                        onRetry = { screenModel.onQueryChange(state.query) },
                    )
                    state.results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.explore_no_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = AgoraSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(AgoraSpacing.listItemSpacing),
                        ) {
                            items(state.results, key = { it.id }) { community ->
                                ExploreCommunityCard(
                                    community = community,
                                    onClick = {
                                        navigator.push(CommunityPreviewScreen(community.id))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreCommunityCard(
    community: Community,
    onClick: () -> Unit,
) {
    MarbleCard(
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
            Spacer(Modifier.height(AgoraSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        Res.string.preview_member_count,
                        community.memberCount ?: 0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (community.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.xs)) {
                        community.tags.take(3).forEach { tag ->
                            val icon = tag.icon?.let { "$it " }.orEmpty()
                            Text(
                                text = icon + tag.nameEs,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
