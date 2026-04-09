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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.GreekFrame
import com.app.community.core.ui.components.GreekKeyDivider
import com.app.community.core.ui.components.PedimentHeader
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors

class RegisterScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<RegisterScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.agoraColors.parchment)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AgoraSpacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(AgoraSpacing.xxl))

            PedimentHeader(
                title = "Crear cuenta",
            )

            Spacer(Modifier.height(AgoraSpacing.xl))

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
                        value = uiState.displayName,
                        onValueChange = screenModel::onDisplayNameChange,
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = screenModel::onEmailChange,
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                    var passwordVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = screenModel::onPasswordChange,
                        label = { Text("Contrasena") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "Ocultar" else "Ver")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                    var confirmPasswordVisible by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = uiState.confirmPassword,
                        onValueChange = screenModel::onConfirmPasswordChange,
                        label = { Text("Confirmar contrasena") },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Text(if (confirmPasswordVisible) "Ocultar" else "Ver")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(AgoraSpacing.sm))

                    if (uiState.status is RegisterStatus.Error) {
                        Text(
                            text = (uiState.status as RegisterStatus.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(AgoraSpacing.sm))
                    }

                    Spacer(modifier = Modifier.height(AgoraSpacing.sm))

                    GreekKeyDivider(
                        color = MaterialTheme.agoraColors.goldLeaf,
                    )

                    Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                    AgoraButton(
                        text = "Registrate",
                        onClick = screenModel::onRegister,
                        variant = AgoraButtonVariant.Primary,
                        enabled = uiState.status !is RegisterStatus.Loading,
                        isLoading = uiState.status is RegisterStatus.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(AgoraSpacing.lg))

                    TextButton(onClick = { navigator.pop() }) {
                        Text(
                            text = "Ya tienes cuenta? Inicia sesion",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AgoraSpacing.xxl))
        }
    }
}
