package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.IonicFrame
import com.app.community.core.ui.components.IonicVoluteHeader
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay

data class JoinCommunityScreen(val initialCode: String = "") : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<JoinCommunityScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        var inviteCode by remember { mutableStateOf(initialCode) }

        LaunchedEffect(Unit) {
            if (initialCode.isNotEmpty()) {
                screenModel.onInviteCodeChange(initialCode)
            }
        }

        LaunchedEffect(uiState) {
            when (uiState) {
                is JoinCommunityScreenModel.UiState.Success -> {
                    delay(1500)
                    navigator.pop()
                }
                is JoinCommunityScreenModel.UiState.Pending -> {
                    delay(2500)
                    navigator.pop()
                }
                else -> Unit
            }
        }

        val isLoading = uiState is JoinCommunityScreenModel.UiState.Loading
        val successState = uiState as? JoinCommunityScreenModel.UiState.Success
        val pendingState = uiState as? JoinCommunityScreenModel.UiState.Pending

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            stringResource(Res.string.join_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back_cd))
                        }
                    },
                )
            },
        ) { padding ->
            Surface(
                color = MaterialTheme.agoraColors.parchment,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AgoraSpacing.screenHorizontal),
                ) {
                    Spacer(Modifier.height(AgoraSpacing.xl))

                    IonicVoluteHeader(title = stringResource(Res.string.join_header))

                    Spacer(Modifier.height(AgoraSpacing.xxl))

                    Text(
                        text = stringResource(Res.string.join_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(AgoraSpacing.xl))

                    IonicFrame {
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { value ->
                                val filtered = value.uppercase().take(8)
                                inviteCode = filtered
                                screenModel.onInviteCodeChange(filtered)
                            },
                            label = { Text(stringResource(Res.string.join_code_label)) },
                            singleLine = true,
                            enabled = !isLoading && successState == null && pendingState == null,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                textAlign = TextAlign.Center,
                                letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing * 2,
                            ),
                        )
                    }

                    Spacer(Modifier.height(AgoraSpacing.xl))

                    val errorState = uiState as? JoinCommunityScreenModel.UiState.Error
                    if (errorState != null) {
                        Text(
                            text = errorState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(AgoraSpacing.sm))
                    }

                    if (successState != null) {
                        Text(
                            text = stringResource(Res.string.join_success, successState.community.name),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else if (pendingState != null) {
                        Text(
                            text = stringResource(Res.string.join_pending_title),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(AgoraSpacing.xs))
                        Text(
                            text = stringResource(Res.string.join_pending_message, pendingState.community.name),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        AgoraButton(
                            text = stringResource(Res.string.join_button),
                            onClick = { screenModel.join() },
                            variant = AgoraButtonVariant.Primary,
                            enabled = !isLoading && inviteCode.length == 8,
                            isLoading = isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
