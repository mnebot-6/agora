package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.MarblePanelShape
import com.app.community.core.ui.theme.agoraColors
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.back_cd
import agora.feature.community.generated.resources.preview_already_member
import agora.feature.community.generated.resources.preview_join_button
import agora.feature.community.generated.resources.preview_member_count
import agora.feature.community.generated.resources.preview_request_join_button
import agora.feature.community.generated.resources.preview_request_sent
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

@Serializable
data class CommunityPreviewScreen(val communityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CommunityPreviewScreenModel> { parametersOf(communityId) }
        val state by screenModel.state.collectAsState()
        val actionMessage by screenModel.actionMessage.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(actionMessage) {
            actionMessage?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearActionMessage()
            }
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            (state as? CommunityPreviewScreenModel.UiState.Content)?.community?.name
                                ?: "",
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when (val s = state) {
                is CommunityPreviewScreenModel.UiState.Loading -> LoadingScreen(Modifier.padding(padding))
                is CommunityPreviewScreenModel.UiState.Error -> ErrorScreen(
                    message = s.message,
                    onRetry = { screenModel.load() },
                    modifier = Modifier.padding(padding),
                )
                is CommunityPreviewScreenModel.UiState.Content -> {
                    val community = s.community
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(AgoraSpacing.screenHorizontal),
                    ) {
                        if (!community.breadcrumb.isNullOrBlank() && community.breadcrumb!!.contains(" › ")) {
                            Text(
                                text = community.breadcrumb!!.substringBeforeLast(" › "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(AgoraSpacing.xs))
                        }
                        if (!community.description.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.agoraColors.parchment,
                                shape = MarblePanelShape,
                            ) {
                                Text(
                                    text = community.description.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.agoraColors.onParchment,
                                    modifier = Modifier.padding(AgoraSpacing.cardInternal),
                                )
                            }
                            Spacer(Modifier.height(AgoraSpacing.md))
                        }

                        Text(
                            stringResource(
                                Res.string.preview_member_count,
                                community.memberCount ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (community.tags.isNotEmpty()) {
                            Spacer(Modifier.height(AgoraSpacing.sm))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                                community.tags.forEach { tag ->
                                    val icon = tag.icon?.let { "$it " }.orEmpty()
                                    Text(
                                        text = icon + tag.nameEs,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(AgoraSpacing.xl))

                        when {
                            s.isAlreadyMember -> {
                                AgoraButton(
                                    text = stringResource(Res.string.preview_already_member),
                                    onClick = {
                                        navigator.replace(CommunityDetailScreen(communityId))
                                    },
                                    variant = AgoraButtonVariant.Primary,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            s.hasPendingRequest -> {
                                Box(
                                    Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(Res.string.preview_request_sent),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                val labelRes = if (community.visibility == CommunityVisibility.PUBLIC_APPROVAL) {
                                    Res.string.preview_request_join_button
                                } else {
                                    Res.string.preview_join_button
                                }
                                AgoraButton(
                                    text = stringResource(labelRes),
                                    onClick = { screenModel.join() },
                                    variant = AgoraButtonVariant.Primary,
                                    isLoading = s.isJoining,
                                    enabled = !s.isJoining,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Auto-navigate when user joins immediately
                        LaunchedEffect(s.joinedNow) {
                            if (s.joinedNow) {
                                navigator.replace(CommunityDetailScreen(communityId))
                            }
                        }
                    }
                }
            }
        }
    }
}
