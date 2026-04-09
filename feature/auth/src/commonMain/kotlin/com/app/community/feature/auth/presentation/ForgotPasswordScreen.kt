package com.app.community.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.GreekFrame
import com.app.community.core.ui.components.PedimentHeader
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors

class ForgotPasswordScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ForgotPasswordScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text("Restablecer contrasena") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                            )
                        }
                    },
                )
            },
            containerColor = MaterialTheme.agoraColors.parchment,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AgoraSpacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(AgoraSpacing.xxl))

                PedimentHeader(
                    title = "Restablecer contrasena",
                )

                Spacer(Modifier.height(AgoraSpacing.sm))

                Text(
                    text = "Introduce tu email y te enviaremos un enlace para restablecerla.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.agoraColors.onParchment,
                    modifier = Modifier.padding(horizontal = AgoraSpacing.xl),
                )

                Spacer(Modifier.height(AgoraSpacing.xl))

                if (uiState.status is ForgotPasswordStatus.Success) {
                    GreekFrame(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.agoraColors.malachiteGreen,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AgoraSpacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Te hemos enviado un enlace a tu email.",
                                color = MaterialTheme.agoraColors.malachiteGreen,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(AgoraSpacing.xl))

                            AgoraButton(
                                text = "Volver",
                                onClick = { navigator.pop() },
                                variant = AgoraButtonVariant.Secondary,
                            )
                        }
                    }
                } else {
                    GreekFrame(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.agoraColors.goldLeaf,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AgoraSpacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OutlinedTextField(
                                value = uiState.email,
                                onValueChange = screenModel::onEmailChange,
                                label = { Text("Email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(AgoraSpacing.sm))

                            if (uiState.status is ForgotPasswordStatus.Error) {
                                Text(
                                    text = (uiState.status as ForgotPasswordStatus.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(AgoraSpacing.sm))
                            }

                            Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                            AgoraButton(
                                text = "Enviar enlace",
                                onClick = screenModel::onSendResetLink,
                                variant = AgoraButtonVariant.Primary,
                                enabled = uiState.status !is ForgotPasswordStatus.Loading,
                                isLoading = uiState.status is ForgotPasswordStatus.Loading,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                            TextButton(onClick = { navigator.pop() }) {
                                Text(
                                    text = "Volver",
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AgoraSpacing.xxl))
            }
        }
    }
}
