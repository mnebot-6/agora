package com.app.community.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import agora.composeapp.generated.resources.Res
import agora.composeapp.generated.resources.*
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.jetbrains.compose.resources.stringResource
import com.app.community.core.common.DeepLinkHandler
import com.app.community.feature.activity.presentation.ActivityDetailScreen
import com.app.community.feature.activity.presentation.GuestActivityScreen
import com.app.community.feature.community.presentation.AutoJoinByInviteScreen
import com.app.community.feature.community.presentation.CommunityListScreen
import com.app.community.feature.notification.presentation.NotificationListScreen
import com.app.community.dashboard.DashboardScreen
import com.app.community.feature.community.presentation.CommunityPaymentsScreen

object AgoraTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.AccountBalance)
            val title = stringResource(Res.string.tab_agora)
            return remember(title) { TabOptions(index = 0u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(DashboardScreen()) { navigator ->
            val pendingActivityCode by DeepLinkHandler.pendingActivityCode.collectAsState()
            val pendingNotificationActivityId by DeepLinkHandler.pendingNotificationActivityId.collectAsState()
            LaunchedEffect(pendingActivityCode) {
                val code = DeepLinkHandler.consumeActivityCode()
                if (code != null) {
                    navigator.push(GuestActivityScreen(code))
                }
            }
            LaunchedEffect(pendingNotificationActivityId) {
                val id = DeepLinkHandler.consumeNotificationActivityId() ?: return@LaunchedEffect
                val current = navigator.lastItem
                if (current is ActivityDetailScreen && current.activityId == id) {
                    navigator.replace(ActivityDetailScreen(id))
                } else {
                    navigator.push(ActivityDetailScreen(id))
                }
            }
            navigator.lastItem.Content()
        }
    }
}

object CommunitiesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Groups)
            val title = stringResource(Res.string.tab_communities)
            return remember(title) { TabOptions(index = 1u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(CommunityListScreen()) { navigator ->
            val pendingConnect by DeepLinkHandler.pendingConnectCommunityId.collectAsState()
            // Vuelta del alta de Stripe. Se hace replace si ya estabamos en esa pantalla
            // para que se recargue y no se apilen dos iguales.
            LaunchedEffect(pendingConnect) {
                val id = DeepLinkHandler.consumeConnectCommunityId() ?: return@LaunchedEffect
                val current = navigator.lastItem
                if (current is CommunityPaymentsScreen && current.communityId == id) {
                    navigator.replace(CommunityPaymentsScreen(id))
                } else {
                    navigator.push(CommunityPaymentsScreen(id))
                }
            }
            val pendingCode by DeepLinkHandler.pendingInviteCode.collectAsState()
            LaunchedEffect(pendingCode) {
                val code = DeepLinkHandler.consumeInviteCode()
                if (code != null) {
                    // Resuelve y une silenciosamente (o pide confirmación si la
                    // comunidad requiere aprobación). El usuario nunca ve la
                    // pantalla manual de "introducir código".
                    navigator.push(AutoJoinByInviteScreen(inviteCode = code))
                }
            }
            navigator.lastItem.Content()
        }
    }
}

object NotificationsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Campaign)
            val title = stringResource(Res.string.tab_notifications)
            return remember(title) { TabOptions(index = 2u, title = title, icon = icon) }
        }

    @Composable
    override fun Content() {
        Navigator(NotificationListScreen())
    }
}
