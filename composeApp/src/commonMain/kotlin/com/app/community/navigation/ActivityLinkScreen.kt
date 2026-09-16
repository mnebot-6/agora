package com.app.community.navigation

import agora.composeapp.generated.resources.Res
import agora.composeapp.generated.resources.activity_link_close
import agora.composeapp.generated.resources.activity_link_not_found
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.data.repository.GuestRepository
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.LoadingScreen
import com.app.community.core.ui.theme.AgoraSpacing
import com.app.community.feature.activity.presentation.ActivityDetailScreen
import com.app.community.feature.community.presentation.CommunityPreviewScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Resuelve el link de una actividad (/a/{code}) para un usuario con sesión:
 * miembro -> detalle de la actividad; no miembro -> ficha de la comunidad para
 * pedir unirse. Sin sesión ni siquiera se llega aquí: App.kt muestra el login y
 * el código espera en DeepLinkHandler.
 */
data class ActivityLinkScreen(val code: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val guestRepository = koinInject<GuestRepository>()
        // null = cargando; "" = link no válido; otro = mensaje de error.
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(code) {
            guestRepository.getPreview(code)
                .onSuccess { preview ->
                    val activity = preview.activity
                    val community = preview.community
                    when {
                        preview.status != "ok" || activity == null || community == null -> error = ""
                        preview.isMember -> navigator.replace(ActivityDetailScreen(activity.id))
                        else -> navigator.replace(CommunityPreviewScreen(community.id))
                    }
                }
                .onError { msg, _ -> error = msg }
        }

        val message = error
        if (message == null) {
            LoadingScreen()
            return
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(AgoraSpacing.screenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message.ifEmpty { stringResource(Res.string.activity_link_not_found) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(AgoraSpacing.xl))
            AgoraButton(
                text = stringResource(Res.string.activity_link_close),
                onClick = { navigator.pop() },
                variant = AgoraButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
