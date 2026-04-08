package com.app.community.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class ForgotPasswordScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ForgotPasswordScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Restablecer contraseña",
                            style = MaterialTheme.typography.headlineMedium,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Introduce tu email y te enviaremos un enlace para restablecerla.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (uiState.status is ForgotPasswordStatus.Success) {
                            Text(
                                text = "Te hemos enviado un enlace a tu email.",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            TextButton(onClick = { navigator.pop() }) {
                                Text("Volver")
                            }
                        } else {
                            OutlinedTextField(
                                value = uiState.email,
                                onValueChange = screenModel::onEmailChange,
                                label = { Text("Email") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.status is ForgotPasswordStatus.Error) {
                                Text(
                                    text = (uiState.status as ForgotPasswordStatus.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = screenModel::onSendResetLink,
                                enabled = uiState.status !is ForgotPasswordStatus.Loading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uiState.status is ForgotPasswordStatus.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text("Enviar enlace")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(onClick = { navigator.pop() }) {
                                Text("Volver")
                            }
                        }
                    }
                }
            }
        }
    }
}
