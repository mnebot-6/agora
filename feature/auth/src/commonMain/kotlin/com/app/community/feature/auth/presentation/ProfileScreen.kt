package com.app.community.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import com.app.community.core.ui.locale.AppLanguage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.FlutedColumnDivider
import com.app.community.core.ui.components.ErrorScreen
import com.app.community.core.ui.components.FriezeBandHeader
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraElevation
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.core.ui.theme.agoraColors
import agora.feature.auth.generated.resources.Res
import agora.feature.auth.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class ProfileScreen : Screen {

    @Composable
    override fun Content() {
        val screenModel = koinScreenModel<ProfileScreenModel>()
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.actionMessage) {
            state.actionMessage?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearActionMessage()
            }
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.profile_title)) },
                    actions = {
                        if (!state.isEditing && state.profile != null) {
                            TextButton(onClick = screenModel::startEditing) {
                                Text(stringResource(Res.string.profile_edit), color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(padding))
                state.error != null -> ErrorScreen(
                    message = state.error!!,
                    onRetry = screenModel::load,
                    modifier = Modifier.padding(padding),
                )
                state.isEditing -> EditProfileContent(
                    displayName = state.editDisplayName,
                    isSaving = state.isSaving,
                    onDisplayNameChange = screenModel::onDisplayNameChange,
                    onSave = screenModel::saveProfile,
                    onCancel = screenModel::cancelEditing,
                    modifier = Modifier.padding(padding),
                )
                state.profile != null -> ProfileContent(
                    profile = state.profile!!,
                    isDarkMode = state.isDarkMode,
                    language = state.language,
                    isDeletingAccount = state.isDeletingAccount,
                    onToggleDarkMode = screenModel::toggleDarkMode,
                    onLanguageChange = screenModel::setLanguage,
                    onSignOut = screenModel::signOut,
                    onDeleteAccount = screenModel::deleteAccount,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: com.app.community.core.model.Profile,
    isDarkMode: Boolean,
    language: AppLanguage,
    isDeletingAccount: Boolean,
    onToggleDarkMode: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    val deleteKeyword = stringResource(Res.string.profile_delete_account_keyword)
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(AgoraSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.lg),
    ) {
        Spacer(Modifier.height(AgoraSpacing.lg))

        // Avatar as carved stone relief (square StoneCard)
        MarbleCard(
            modifier = Modifier.size(96.dp),
            elevation = AgoraElevation.standard,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        FlutedColumnDivider(modifier = Modifier.padding(horizontal = AgoraSpacing.xxl))

        FriezeBandHeader(title = stringResource(Res.string.profile_title))

        Text(
            text = profile.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        FlutedColumnDivider(modifier = Modifier.padding(horizontal = AgoraSpacing.xxl))

        MarbleCard(elevation = AgoraElevation.subtle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.md, vertical = AgoraSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.profile_dark_mode),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleDarkMode() },
                )
            }
        }

        // Idioma de la app
        MarbleCard(elevation = AgoraElevation.subtle) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AgoraSpacing.md, vertical = AgoraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.profile_language_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                LanguageOption(
                    label = stringResource(Res.string.profile_language_auto),
                    selected = language == AppLanguage.AUTO,
                    onSelect = { onLanguageChange(AppLanguage.AUTO) },
                )
                LanguageOption(
                    label = stringResource(Res.string.profile_language_es),
                    selected = language == AppLanguage.ES,
                    onSelect = { onLanguageChange(AppLanguage.ES) },
                )
                LanguageOption(
                    label = stringResource(Res.string.profile_language_en),
                    selected = language == AppLanguage.EN,
                    onSelect = { onLanguageChange(AppLanguage.EN) },
                )
            }
        }

        Spacer(Modifier.height(AgoraSpacing.xl))

        AgoraButton(
            text = stringResource(Res.string.profile_sign_out),
            onClick = { showSignOutDialog = true },
            variant = AgoraButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(AgoraSpacing.md))

        TextButton(
            onClick = {
                deleteConfirmText = ""
                showDeleteAccountDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.profile_delete_account),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(AgoraSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.profile_privacy_policy),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { uriHandler.openUri("https://share-agora.app/privacy") }
                    .padding(AgoraSpacing.xs),
            )
            Spacer(Modifier.size(AgoraSpacing.md))
            Text(
                text = stringResource(Res.string.profile_terms_of_service),
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { uriHandler.openUri("https://share-agora.app/terms") }
                    .padding(AgoraSpacing.xs),
            )
        }

        Spacer(Modifier.height(AgoraSpacing.lg))
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(Res.string.profile_sign_out)) },
            text = { Text(stringResource(Res.string.profile_sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    onSignOut()
                }) {
                    Text(stringResource(Res.string.profile_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) showDeleteAccountDialog = false
            },
            title = { Text(stringResource(Res.string.profile_delete_account_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm)) {
                    Text(stringResource(Res.string.profile_delete_account_confirm_body))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        label = { Text(stringResource(Res.string.profile_delete_account_type_to_confirm)) },
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAccount()
                    },
                    enabled = !isDeletingAccount && deleteConfirmText.trim() == deleteKeyword,
                ) {
                    Text(
                        text = stringResource(Res.string.profile_delete_account_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount,
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = AgoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.size(AgoraSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EditProfileContent(
    displayName: String,
    isSaving: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.agoraColors.parchment)
            .padding(AgoraSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.lg),
    ) {
        FriezeBandHeader(title = stringResource(Res.string.profile_edit_title))

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(Res.string.name)) },
            singleLine = true,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
        ) {
            AgoraButton(
                text = stringResource(Res.string.cancel),
                onClick = onCancel,
                variant = AgoraButtonVariant.Tertiary,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            )
            AgoraButton(
                text = stringResource(Res.string.profile_save),
                onClick = onSave,
                variant = AgoraButtonVariant.Primary,
                enabled = !isSaving && displayName.isNotBlank(),
                isLoading = isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
