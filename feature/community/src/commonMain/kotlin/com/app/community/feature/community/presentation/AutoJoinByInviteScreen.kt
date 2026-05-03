package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.invite_auto_join_request_sent_title
import agora.feature.community.generated.resources.invite_auto_join_request_sent_message
import agora.feature.community.generated.resources.invite_auto_join_close
import agora.feature.community.generated.resources.invite_auto_join_invalid
import agora.feature.community.generated.resources.invite_auto_join_confirm_title
import agora.feature.community.generated.resources.invite_auto_join_confirm_message
import agora.feature.community.generated.resources.invite_auto_join_confirm_ok
import agora.feature.community.generated.resources.invite_auto_join_confirm_cancel

/**
 * Pantalla intermedia que se abre cuando el usuario llega vía deep link de
 * invitación (https://share-agora.app/c/{code} o agora://invite/{code}).
 *
 * Resuelve el código sin pedir input al usuario:
 * - Ya es miembro → navega a CommunityDetailScreen
 * - Comunidad pública abierta → se une silenciosamente y navega
 * - Privada / aprobación → muestra dialog de confirmación; al confirmar
 *   envía la solicitud y muestra una pantalla de "solicitud enviada"
 * - Inválida → mensaje de error con botón cerrar
 */
data class AutoJoinByInviteScreen(val inviteCode: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<AutoJoinByInviteScreenModel> { parametersOf(inviteCode) }
        val state by screenModel.state.collectAsState()

        // Auto-navigate cuando ya somos miembros o nos hemos unido directamente
        LaunchedEffect(state) {
            val current = state
            if (current is AutoJoinByInviteScreenModel.UiState.NavigateToCommunity) {
                navigator.replace(CommunityDetailScreen(current.communityId))
            }
        }

        when (val s = state) {
            AutoJoinByInviteScreenModel.UiState.Loading,
            is AutoJoinByInviteScreenModel.UiState.NavigateToCommunity -> {
                LoadingScreen()
            }

            is AutoJoinByInviteScreenModel.UiState.ConfirmApproval -> {
                AlertDialog(
                    onDismissRequest = { navigator.pop() },
                    title = { Text(stringResource(Res.string.invite_auto_join_confirm_title)) },
                    text = {
                        Text(
                            stringResource(
                                Res.string.invite_auto_join_confirm_message,
                                s.community.name,
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { screenModel.confirmRequest() }) {
                            Text(stringResource(Res.string.invite_auto_join_confirm_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { navigator.pop() }) {
                            Text(stringResource(Res.string.invite_auto_join_confirm_cancel))
                        }
                    },
                )
                // Detrás del dialog, una pantalla de loading neutra
                LoadingScreen()
            }

            is AutoJoinByInviteScreenModel.UiState.RequestSent -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AgoraSpacing.screenHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.invite_auto_join_request_sent_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(AgoraSpacing.md))
                    Text(
                        text = stringResource(
                            Res.string.invite_auto_join_request_sent_message,
                            s.community.name,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(AgoraSpacing.xl))
                    AgoraButton(
                        text = stringResource(Res.string.invite_auto_join_close),
                        onClick = { navigator.pop() },
                        variant = AgoraButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is AutoJoinByInviteScreenModel.UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AgoraSpacing.screenHorizontal),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(AgoraSpacing.xl))
                    AgoraButton(
                        text = stringResource(Res.string.invite_auto_join_close),
                        onClick = { navigator.pop() },
                        variant = AgoraButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
