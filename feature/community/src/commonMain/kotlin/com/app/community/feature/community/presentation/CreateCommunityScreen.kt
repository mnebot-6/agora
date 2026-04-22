package com.app.community.feature.community.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.app.community.core.model.CommunityVisibility
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.IonicVoluteHeader
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.back_cd
import agora.feature.community.generated.resources.create_community_button
import agora.feature.community.generated.resources.create_community_description_label
import agora.feature.community.generated.resources.create_community_header
import agora.feature.community.generated.resources.create_community_name_label
import agora.feature.community.generated.resources.create_community_tags_label
import agora.feature.community.generated.resources.create_community_title
import agora.feature.community.generated.resources.create_community_visibility_label
import agora.feature.community.generated.resources.create_community_visibility_private
import agora.feature.community.generated.resources.create_community_visibility_private_desc
import agora.feature.community.generated.resources.create_community_visibility_public_approval
import agora.feature.community.generated.resources.create_community_visibility_public_approval_desc
import agora.feature.community.generated.resources.create_community_visibility_public_open
import agora.feature.community.generated.resources.create_community_visibility_public_open_desc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

class CreateCommunityScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CreateCommunityScreenModel>()
        val uiState by screenModel.uiState.collectAsState()
        val form by screenModel.form.collectAsState()

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
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back_cd),
                            )
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
                        .verticalScroll(rememberScrollState())
                        .padding(AgoraSpacing.screenHorizontal),
                ) {
                    Spacer(Modifier.height(AgoraSpacing.xl))

                    IonicVoluteHeader(title = stringResource(Res.string.create_community_header))

                    Spacer(Modifier.height(AgoraSpacing.xxl))

                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { screenModel.onNameChange(it) },
                        label = { Text(stringResource(Res.string.create_community_name_label)) },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(AgoraSpacing.lg))

                    OutlinedTextField(
                        value = form.description,
                        onValueChange = { screenModel.onDescriptionChange(it) },
                        label = { Text(stringResource(Res.string.create_community_description_label)) },
                        minLines = 3,
                        maxLines = 5,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(AgoraSpacing.lg))

                    // Visibility
                    Text(
                        text = stringResource(Res.string.create_community_visibility_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(AgoraSpacing.sm))
                    CommunityVisibility.entries.forEach { visibility ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) {
                                    screenModel.onVisibilityChange(visibility)
                                }
                                .padding(vertical = AgoraSpacing.xs),
                        ) {
                            RadioButton(
                                selected = form.visibility == visibility,
                                onClick = { screenModel.onVisibilityChange(visibility) },
                                enabled = !isLoading,
                            )
                            Spacer(Modifier.width(AgoraSpacing.sm))
                            Column {
                                Text(
                                    text = stringResource(visibility.labelRes()),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(visibility.descRes()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(AgoraSpacing.lg))

                    // Tags
                    Text(
                        text = stringResource(Res.string.create_community_tags_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(AgoraSpacing.sm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
                    ) {
                        form.availableTags.forEach { tag ->
                            val selected = form.selectedTagIds.contains(tag.id)
                            FilterChip(
                                selected = selected,
                                onClick = { screenModel.onTagToggle(tag.id) },
                                label = {
                                    val icon = tag.icon?.let { "$it " }.orEmpty()
                                    Text(icon + tag.nameEs)
                                },
                                enabled = !isLoading && (selected || form.selectedTagIds.size < 3),
                            )
                        }
                    }

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
                        enabled = !isLoading && form.name.isNotBlank() && form.selectedTagIds.isNotEmpty(),
                        isLoading = isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(AgoraSpacing.xl))
                }
            }
        }
    }
}

private fun CommunityVisibility.labelRes(): StringResource = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> Res.string.create_community_visibility_public_open
    CommunityVisibility.PUBLIC_APPROVAL -> Res.string.create_community_visibility_public_approval
    CommunityVisibility.PRIVATE -> Res.string.create_community_visibility_private
}

private fun CommunityVisibility.descRes(): StringResource = when (this) {
    CommunityVisibility.PUBLIC_OPEN -> Res.string.create_community_visibility_public_open_desc
    CommunityVisibility.PUBLIC_APPROVAL -> Res.string.create_community_visibility_public_approval_desc
    CommunityVisibility.PRIVATE -> Res.string.create_community_visibility_private_desc
}
