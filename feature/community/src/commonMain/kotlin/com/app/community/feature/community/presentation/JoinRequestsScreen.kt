package com.app.community.feature.community.presentation

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.back_cd
import agora.feature.community.generated.resources.join_requests_approve
import agora.feature.community.generated.resources.join_requests_empty
import agora.feature.community.generated.resources.join_requests_reject
import agora.feature.community.generated.resources.join_requests_title
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

data class JoinRequestsScreen(val communityId: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<JoinRequestsScreenModel> { parametersOf(communityId) }
        val state by screenModel.uiState.collectAsState()
        val inProgress by screenModel.actionInProgress.collectAsState()

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.join_requests_title)) },
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
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    is JoinRequestsScreenModel.UiState.Loading -> {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    is JoinRequestsScreenModel.UiState.Error -> {
                        Text(
                            s.message,
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is JoinRequestsScreenModel.UiState.Loaded -> {
                        if (s.requests.isEmpty()) {
                            Text(
                                stringResource(Res.string.join_requests_empty),
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(AgoraSpacing.screenHorizontal),
                                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.md),
                            ) {
                                items(s.requests, key = { it.id }) { req ->
                                    RequestCard(
                                        name = req.profiles?.displayName ?: "—",
                                        message = req.message,
                                        isProcessing = inProgress == req.id,
                                        onApprove = { screenModel.approve(req.id) },
                                        onReject = { screenModel.reject(req.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    name: String,
    message: String?,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AgoraSpacing.md)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(AgoraSpacing.sm))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(AgoraSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                AgoraButton(
                    text = stringResource(Res.string.join_requests_reject),
                    onClick = onReject,
                    variant = AgoraButtonVariant.Secondary,
                    enabled = !isProcessing,
                    isLoading = isProcessing,
                    modifier = Modifier.weight(1f),
                )
                AgoraButton(
                    text = stringResource(Res.string.join_requests_approve),
                    onClick = onApprove,
                    variant = AgoraButtonVariant.Primary,
                    enabled = !isProcessing,
                    isLoading = isProcessing,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
