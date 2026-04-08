package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
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
            if (uiState is JoinCommunityScreenModel.UiState.Success) {
                delay(1500)
                navigator.pop()
            }
        }

        val isLoading = uiState is JoinCommunityScreenModel.UiState.Loading
        val successState = uiState as? JoinCommunityScreenModel.UiState.Success

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Unirse a comunidad") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Introduce el código de 8 caracteres para unirte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { value ->
                        val filtered = value.uppercase().take(8)
                        inviteCode = filtered
                        screenModel.onInviteCodeChange(filtered)
                    },
                    label = { Text("Código de invitación") },
                    singleLine = true,
                    enabled = !isLoading && successState == null,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing * 2,
                    ),
                )

                Spacer(Modifier.height(24.dp))

                val errorState = uiState as? JoinCommunityScreenModel.UiState.Error
                if (errorState != null) {
                    Text(
                        text = errorState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (successState != null) {
                    Text(
                        text = "¡Ya estás en \"${successState.community.name}\"!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Button(
                        onClick = { screenModel.join() },
                        enabled = !isLoading && inviteCode.length == 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Unirse")
                        }
                    }
                }
            }
        }
    }
}
