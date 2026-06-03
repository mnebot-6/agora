package com.app.community.feature.activity.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.data.repository.AuthRepository
import com.app.community.core.model.SlotMode
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import agora.feature.activity.generated.resources.Res
import agora.feature.activity.generated.resources.guest_approved_message
import agora.feature.activity.generated.resources.guest_approved_title
import agora.feature.activity.generated.resources.guest_capacity
import agora.feature.activity.generated.resources.guest_capacity_unlimited
import agora.feature.activity.generated.resources.guest_exit
import agora.feature.activity.generated.resources.guest_form_error
import agora.feature.activity.generated.resources.guest_full_message
import agora.feature.activity.generated.resources.guest_full_title
import agora.feature.activity.generated.resources.guest_invite_intro
import agora.feature.activity.generated.resources.guest_name_label
import agora.feature.activity.generated.resources.guest_not_found
import agora.feature.activity.generated.resources.guest_pending_message
import agora.feature.activity.generated.resources.guest_pending_title
import agora.feature.activity.generated.resources.guest_phone_label
import agora.feature.activity.generated.resources.guest_rejected_message
import agora.feature.activity.generated.resources.guest_rejected_title
import agora.feature.activity.generated.resources.guest_request_button
import agora.feature.activity.generated.resources.guest_when
import agora.feature.activity.generated.resources.guest_where

/**
 * Pantalla de invitado a una actividad. Se monta:
 *  - como raíz confinada cuando la sesión es anónima (web→app / sin login),
 *  - o empujada cuando un usuario real no miembro abre el link.
 * Si el caller ya es miembro, navega al detalle real de la actividad.
 */
data class GuestActivityScreen(val code: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val authRepository = koinInject<AuthRepository>()
        val scope = rememberCoroutineScope()
        val screenModel = koinScreenModel<GuestActivityScreenModel> { parametersOf(code) }
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state) {
            val s = state
            if (s is GuestActivityScreenModel.UiState.NavigateToActivity) {
                navigator.replace(ActivityDetailScreen(s.activityId))
            }
        }

        val onClose: () -> Unit = {
            if (authRepository.isAnonymous()) {
                scope.launch { authRepository.signOut() }
            } else {
                navigator.pop()
            }
        }

        when (val s = state) {
            GuestActivityScreenModel.UiState.Loading,
            is GuestActivityScreenModel.UiState.NavigateToActivity -> LoadingScreen()

            GuestActivityScreenModel.UiState.NotFound ->
                MessageScreen(stringResource(Res.string.guest_not_found), onClose)

            GuestActivityScreenModel.UiState.Full ->
                TitledMessageScreen(
                    title = stringResource(Res.string.guest_full_title),
                    message = stringResource(Res.string.guest_full_message),
                    onClose = onClose,
                )

            is GuestActivityScreenModel.UiState.Error ->
                MessageScreen(s.message, onClose)

            is GuestActivityScreenModel.UiState.Pending ->
                TitledMessageScreen(
                    title = stringResource(Res.string.guest_pending_title),
                    message = stringResource(Res.string.guest_pending_message, s.activityName),
                    onClose = onClose,
                )

            is GuestActivityScreenModel.UiState.Approved ->
                TitledMessageScreen(
                    title = stringResource(Res.string.guest_approved_title),
                    message = stringResource(Res.string.guest_approved_message, s.activityName),
                    onClose = onClose,
                )

            is GuestActivityScreenModel.UiState.Rejected ->
                TitledMessageScreen(
                    title = stringResource(Res.string.guest_rejected_title),
                    message = stringResource(Res.string.guest_rejected_message, s.activityName),
                    onClose = onClose,
                )

            is GuestActivityScreenModel.UiState.Form ->
                GuestForm(state = s, onSubmit = screenModel::submit)
        }
    }

    @Composable
    private fun GuestForm(
        state: GuestActivityScreenModel.UiState.Form,
        onSubmit: (String, String) -> Unit,
    ) {
        val activity = state.preview.activity ?: return
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.guest_invite_intro),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AgoraSpacing.sm))
            Text(
                text = activity.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(AgoraSpacing.md))
            Text(
                text = stringResource(Res.string.guest_when, formatDateTime(activity.datetime)),
                style = MaterialTheme.typography.bodyMedium,
            )
            activity.locationName?.let {
                Text(
                    text = stringResource(Res.string.guest_where, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = if (activity.slotMode == SlotMode.UNLIMITED || activity.capacity == null) {
                    stringResource(Res.string.guest_capacity_unlimited)
                } else {
                    stringResource(Res.string.guest_capacity, activity.taken, activity.capacity!!)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(AgoraSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.guest_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(AgoraSpacing.sm))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(Res.string.guest_phone_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.error != null) {
                Spacer(Modifier.height(AgoraSpacing.sm))
                Text(
                    text = if (state.error == "validation") {
                        stringResource(Res.string.guest_form_error)
                    } else state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(AgoraSpacing.lg))
            AgoraButton(
                text = stringResource(Res.string.guest_request_button),
                onClick = { onSubmit(name, phone) },
                variant = AgoraButtonVariant.Primary,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun TitledMessageScreen(title: String, message: String, onClose: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(AgoraSpacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AgoraSpacing.xl))
            AgoraButton(
                text = stringResource(Res.string.guest_exit),
                onClick = onClose,
                variant = AgoraButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun MessageScreen(message: String, onClose: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(AgoraSpacing.xl))
            AgoraButton(
                text = stringResource(Res.string.guest_exit),
                onClick = onClose,
                variant = AgoraButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatDateTime(instant: kotlinx.datetime.Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    fun p(n: Int) = n.toString().padStart(2, '0')
    return "${p(dt.dayOfMonth)}/${p(dt.monthNumber)}/${dt.year} ${p(dt.hour)}:${p(dt.minute)}"
}
