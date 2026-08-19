package com.app.community.feature.community.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.app.community.core.ui.components.AgoraButton
import com.app.community.core.ui.components.AgoraButtonVariant
import com.app.community.core.ui.components.AgoraTopBar
import com.app.community.core.ui.components.MarbleCard
import com.app.community.core.ui.theme.AgoraSpacing
import agora.feature.community.generated.resources.Res
import agora.feature.community.generated.resources.back_cd
import agora.feature.community.generated.resources.payments_active_body
import agora.feature.community.generated.resources.payments_active_title
import agora.feature.community.generated.resources.payments_continue_button
import agora.feature.community.generated.resources.payments_incomplete_body
import agora.feature.community.generated.resources.payments_incomplete_title
import agora.feature.community.generated.resources.payments_none_body
import agora.feature.community.generated.resources.payments_none_title
import agora.feature.community.generated.resources.payments_setup_button
import agora.feature.community.generated.resources.payments_title
import agora.feature.community.generated.resources.payments_who_charges
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

/** Cobros de una comunidad: alta en Stripe y estado de la verificacion. Solo admins. */
data class CommunityPaymentsScreen(val communityId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<CommunityPaymentsScreenModel> { parametersOf(communityId) }
        val state by screenModel.state.collectAsState()
        val uriHandler = LocalUriHandler.current

        LaunchedEffect(Unit) { screenModel.load() }

        // El formulario de alta lo aloja Stripe: se sale al navegador y se vuelve por
        // /pay/connect. Ningun dato de identidad ni bancario pasa por Agora.
        LaunchedEffect(state.onboardingUrl) {
            state.onboardingUrl?.let {
                uriHandler.openUri(it)
                screenModel.consumeOnboardingUrl()
            }
        }

        Scaffold(
            topBar = {
                AgoraTopBar(
                    title = { Text(stringResource(Res.string.payments_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back_cd))
                        }
                    },
                )
            },
        ) { padding ->
            if (state.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(AgoraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AgoraSpacing.md),
            ) {
                val status = state.status
                val chargesEnabled = status?.chargesEnabled == true

                // Tres estados: sin cuenta, alta a medias, y cobrando. El boton es el
                // mismo en los dos primeros porque Stripe reanuda el alta donde se quedo.
                val (titleRes, bodyRes) = when {
                    chargesEnabled -> Res.string.payments_active_title to Res.string.payments_active_body
                    status?.isIncomplete == true ->
                        Res.string.payments_incomplete_title to Res.string.payments_incomplete_body
                    else -> Res.string.payments_none_title to Res.string.payments_none_body
                }

                MarbleCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(AgoraSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AgoraSpacing.sm),
                    ) {
                        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(bodyRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!chargesEnabled) {
                    AgoraButton(
                        text = stringResource(
                            if (status?.isIncomplete == true) {
                                Res.string.payments_continue_button
                            } else {
                                Res.string.payments_setup_button
                            },
                        ),
                        onClick = screenModel::startOnboarding,
                        variant = AgoraButtonVariant.Primary,
                        enabled = !state.isStartingOnboarding,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Quien cobra es el admin, no Agora. Decirlo aqui evita la duda de a donde
                // va el dinero, que es la primera pregunta que hace cualquiera.
                Text(
                    text = stringResource(Res.string.payments_who_charges),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
