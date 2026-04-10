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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.IonicVoluteHeader
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class CreateCommunityScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CreateCommunityScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        LaunchedEffect(uiState) {
            if (uiState is CreateCommunityScreenModel.UiState.Success) {
                navigator.pop()
            }
        }

        val isLoading = uiState is CreateCommunityScreenModel.UiState.Loading

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = {
                        Text(
                            stringResource(Res.string.create_community_title),
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

                    IonicVoluteHeader(title = stringResource(Res.string.create_community_header))

                    Spacer(Modifier.height(AgoraSpacing.xxl))

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            screenModel.onNameChange(it)
                        },
                        label = { Text(stringResource(Res.string.create_community_name_label)) },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(AgoraSpacing.lg))

                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            screenModel.onDescriptionChange(it)
                        },
                        label = { Text(stringResource(Res.string.create_community_description_label)) },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(AgoraSpacing.xl))

                    val errorState = uiState as? CreateCommunityScreenModel.UiState.Error
                    if (errorState != null) {
                        Text(
                            text = errorState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(AgoraSpacing.sm))
                    }

                    AgoraButton(
                        text = stringResource(Res.string.create_community_button),
                        onClick = { screenModel.create() },
                        variant = AgoraButtonVariant.Primary,
                        enabled = !isLoading && name.isNotBlank(),
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
